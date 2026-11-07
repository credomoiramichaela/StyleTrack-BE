package com.styletrack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.styletrack.payment.PayMongoClient;
import com.styletrack.payment.PayMongoSignature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GCash / QR Ph payments with PayMongo itself replaced by a mock. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:styletrack_pay;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "styletrack.paymongo.webhook-secret=whsk_test_secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnlinePaymentIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsk_test_secret";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @MockBean
    private PayMongoClient payMongo;

    @Test
    void customerPaysWithGcashAndQrPhThroughPayMongo() throws Exception {
        when(payMongo.isConfigured()).thenReturn(true);
        String admin = login("admin", "ChangeMe123!");
        long serviceId = id(send(post("/api/services"), admin, Map.of(
                "name", "Hair Color", "price", 1800.50, "durationMinutes", 60, "homeServiceAvailable", false))
                .andExpect(status().isCreated()));
        long stylistId = id(send(post("/api/stylists"), admin, Map.of(
                "fullName", "Aira Cruz", "username", "aira", "password", "Stylist@123")).andExpect(status().isCreated()));
        for (DayOfWeek day : DayOfWeek.values()) {
            send(post("/api/stylists/" + stylistId + "/schedules"), admin, Map.of(
                    "dayOfWeek", day.name(), "startTime", "09:00", "endTime", "18:00",
                    "homeServiceAvailable", false)).andExpect(status().isCreated());
        }
        String customer = register("Maria Santos", "09175550301");
        String stranger = register("Not Maria", "09175550302");

        long apptId = id(send(post("/api/me/appointments"), customer, Map.of(
                "stylistId", stylistId, "serviceId", serviceId, "type", "SALON",
                "startTime", LocalDate.now().plusDays(3) + "T10:00:00")).andExpect(status().isCreated()));

        // options tell the app whether to show the pay buttons
        mvc.perform(get("/api/me/payment-options").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.methods[0]").value("GCASH"));

        // a booking the salon hasn't confirmed yet can't be paid; nothing is sent to PayMongo
        send(post("/api/me/appointments/" + apptId + "/pay"), customer, Map.of("method", "GCASH"))
                .andExpect(status().isBadRequest());
        verify(payMongo, never()).start(anyLong(), anyString(), anyString(), anyMap(), anyString());
        mvc.perform(put("/api/appointments/" + apptId + "/accept").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        // nobody else can pay for it or look at it, and unknown methods are refused
        send(post("/api/me/appointments/" + apptId + "/pay"), stranger, Map.of("method", "GCASH"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/appointments/" + apptId + "/online-payment").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        send(post("/api/me/appointments/" + apptId + "/pay"), customer, Map.of("method", "CARD"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/appointments/" + apptId + "/online-payment").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NONE"));

        // GCash: the amount goes to PayMongo in centavos, the app gets the redirect link
        when(payMongo.start(eq(180050L), eq("GCASH"), anyString(), anyMap(), anyString()))
                .thenReturn(new PayMongoClient.Started("pi_gcash_1", "awaiting_next_action", "https://pay.example/gcash", null, null));
        send(post("/api/me/appointments/" + apptId + "/pay"), customer, Map.of("method", "gcash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value("https://pay.example/gcash"))
                .andExpect(jsonPath("$.amount").value(1800.50))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // still unpaid while PayMongo says so
        when(payMongo.retrieve("pi_gcash_1")).thenReturn(
                new PayMongoClient.Intent("pi_gcash_1", "awaiting_next_action", 180050L, "PHP", null, null));
        mvc.perform(get("/api/me/appointments/" + apptId + "/online-payment").header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/me/appointments/" + apptId).header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));

        // a webhook with a bad signature is refused and changes nothing
        String paidEvent = event("payment.paid", "pi_gcash_1");
        when(payMongo.retrieve("pi_gcash_1")).thenReturn(
                new PayMongoClient.Intent("pi_gcash_1", "succeeded", 180050L, "PHP", "pay_1", null));
        mvc.perform(post("/api/payments/paymongo/webhook").contentType(MediaType.APPLICATION_JSON).content(paidEvent)
                        .header("Paymongo-Signature", "t=1,te=deadbeef,li="))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/paymongo/webhook").contentType(MediaType.APPLICATION_JSON).content(paidEvent))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/appointments/" + apptId).header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));

        // a correctly signed webhook marks the booking paid, with the PayMongo payment id as the reference
        mvc.perform(post("/api/payments/paymongo/webhook").contentType(MediaType.APPLICATION_JSON).content(paidEvent)
                        .header("Paymongo-Signature", signature(paidEvent)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/appointments/" + apptId).header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("GCASH"))
                .andExpect(jsonPath("$.paymentReference").value("pay_1"));
        mvc.perform(get("/api/me/appointments/" + apptId + "/online-payment").header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.content[0].type").value("PAYMENT_RECEIVED"));

        // repeated webhooks are harmless, and a paid booking can't be paid again
        mvc.perform(post("/api/payments/paymongo/webhook").contentType(MediaType.APPLICATION_JSON).content(paidEvent)
                        .header("Paymongo-Signature", signature(paidEvent)))
                .andExpect(status().isOk());
        send(post("/api/me/appointments/" + apptId + "/pay"), customer, Map.of("method", "QRPH"))
                .andExpect(status().isConflict());

        // the public return page needs no login
        mvc.perform(get("/payment-return")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("StyleTrack")));
    }

    @Test
    void qrPhIsConfirmedByTheStatusCheckWithoutAWebhook() throws Exception {
        when(payMongo.isConfigured()).thenReturn(true);
        String admin = login("admin", "ChangeMe123!");
        long serviceId = id(send(post("/api/services"), admin, Map.of(
                "name", "Haircut", "price", 350, "durationMinutes", 30, "homeServiceAvailable", false))
                .andExpect(status().isCreated()));
        long stylistId = id(send(post("/api/stylists"), admin, Map.of(
                "fullName", "Leo Reyes", "username", "leo", "password", "Stylist@123")).andExpect(status().isCreated()));
        for (DayOfWeek day : DayOfWeek.values()) {
            send(post("/api/stylists/" + stylistId + "/schedules"), admin, Map.of(
                    "dayOfWeek", day.name(), "startTime", "09:00", "endTime", "18:00",
                    "homeServiceAvailable", false)).andExpect(status().isCreated());
        }
        String customer = register("Rosa Lim", "09175550311");
        long apptId = id(send(post("/api/me/appointments"), customer, Map.of(
                "stylistId", stylistId, "serviceId", serviceId, "type", "SALON",
                "startTime", LocalDate.now().plusDays(2) + "T11:00:00")).andExpect(status().isCreated()));
        mvc.perform(put("/api/appointments/" + apptId + "/accept").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        when(payMongo.start(eq(35000L), eq("QRPH"), anyString(), anyMap(), anyString()))
                .thenReturn(new PayMongoClient.Started("pi_qr_1", "awaiting_next_action", null, "data:image/png;base64,AAAA", null));
        send(post("/api/me/appointments/" + apptId + "/pay"), customer, Map.of("method", "QR Ph"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.qrImage").value("data:image/png;base64,AAAA"))
                .andExpect(jsonPath("$.redirectUrl").doesNotExist());

        // PayMongo reports a different amount than we asked for: the booking must NOT be marked paid
        when(payMongo.retrieve("pi_qr_1")).thenReturn(
                new PayMongoClient.Intent("pi_qr_1", "succeeded", 100L, "PHP", "pay_qr_1", null));
        mvc.perform(get("/api/me/appointments/" + apptId + "/online-payment").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/appointments/" + apptId).header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
    }

    // ---------------------------------------------------------------- helpers

    private String event(String type, String intentId) throws Exception {
        return json.writeValueAsString(Map.of("data", Map.of("id", "evt_1", "type", "event", "attributes", Map.of(
                "type", type, "livemode", false,
                "data", Map.of("id", "pay_1", "type", "payment", "attributes", Map.of("payment_intent_id", intentId))))));
    }

    private static String signature(String body) {
        String t = "1700000000";
        return "t=" + t + ",te=" + PayMongoSignature.sign(t + "." + body, WEBHOOK_SECRET) + ",li=";
    }

    private String register(String name, String phone) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", name, "phone", phone, "password", "Password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private String login(String username, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("username", username, "password", password));
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        return mvc.perform(builder.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private long id(ResultActions actions) throws Exception {
        JsonNode node = json.readTree(actions.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }
}
