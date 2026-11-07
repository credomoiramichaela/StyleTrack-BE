package com.styletrack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end smoke test of the main salon workflow, run against an in-memory database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void fullBookingLifecycle() throws Exception {
        String admin = login("admin", "ChangeMe123!");

        // unauthenticated access is rejected
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());

        long serviceId = id(send(post("/api/services"), admin, Map.of(
                "name", "Haircut", "price", 500, "durationMinutes", 60, "homeServiceAvailable", true))
                .andExpect(status().isCreated()));
        long stylistId = id(send(post("/api/stylists"), admin, Map.of(
                "fullName", "Anna Reyes", "username", "anna", "password", "Stylist@123"))
                .andExpect(status().isCreated()));
        for (DayOfWeek day : DayOfWeek.values()) {
            send(post("/api/stylists/" + stylistId + "/schedules"), admin, Map.of(
                    "dayOfWeek", day.name(), "startTime", "09:00", "endTime", "18:00",
                    "homeServiceAvailable", true)).andExpect(status().isCreated());
        }
        long customerId = id(send(post("/api/customers"), admin, Map.of(
                "fullName", "Maria Cruz", "phone", "09171234567")).andExpect(status().isCreated()));

        LocalDate day = LocalDate.now().plusDays(3);
        Map<String, Object> booking = Map.of("customerId", customerId, "stylistId", stylistId,
                "serviceId", serviceId, "type", "HOME_SERVICE", "startTime", day + "T10:00:00",
                "address", "12 Rizal St");

        long apptId = id(send(post("/api/appointments"), admin, booking)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")));

        // double booking (same stylist, same time) is refused
        send(post("/api/appointments"), admin, booking).andExpect(status().isConflict());

        // a stylist logs in, sees only their own queue, accepts and completes
        String stylist = login("anna", "Stylist@123");
        mvc.perform(get("/api/appointments/home-service?status=PENDING").header("Authorization", "Bearer " + stylist))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put("/api/appointments/" + apptId + "/accept").header("Authorization", "Bearer " + stylist))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(put("/api/appointments/" + apptId + "/complete").header("Authorization", "Bearer " + stylist))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pointsEarned").value(5));

        // stylists cannot manage services or view reports
        send(post("/api/services"), stylist, Map.of("name", "X", "price", 1, "durationMinutes", 30))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/summary").header("Authorization", "Bearer " + stylist))
                .andExpect(status().isForbidden());

        // loyalty points were credited and the reports reflect the completed job
        mvc.perform(get("/api/loyalty/customers/" + customerId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(5));
        mvc.perform(get("/api/reports/summary?from=" + day + "&to=" + day).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAppointments").value(1))
                .andExpect(jsonPath("$.revenue").value(500));
        mvc.perform(get("/api/reports/top-services?from=" + day + "&to=" + day).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].serviceName").value("Haircut"));
        mvc.perform(get("/api/reports/revenue-daily?from=" + day + "&to=" + day).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].date").value(day.toString()));
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/recommendations/customer/" + customerId + "/suggestions")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/recommendations/customer/" + customerId + "/generate")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/customers/" + customerId + "/history").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

        // QR Ph payments need a reference number, and a reference can't be reused
        send(put("/api/appointments/" + apptId + "/payment"), admin, Map.of("method", "QR Ph"))
                .andExpect(status().isBadRequest());
        send(put("/api/appointments/" + apptId + "/payment"), admin, Map.of("method", "Cheque"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/payments").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outstandingCount").value(1));
        send(put("/api/appointments/" + apptId + "/payment"), admin, Map.of("method", "QR Ph", "reference", "QR-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("QRPH"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        mvc.perform(get("/api/reports/payments").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCollected").value(500))
                .andExpect(jsonPath("$.byMethod[0].method").value("QRPH"))
                .andExpect(jsonPath("$.outstandingCount").value(0));

        // the stylist records the treatment with a follow-up; staff can read it, and it drives a recommendation
        long hairSpaId = id(send(post("/api/services"), admin, Map.of(
                "name", "Hair Spa", "price", 800, "durationMinutes", 60)).andExpect(status().isCreated()));
        long treatmentId = id(send(post("/api/treatments"), stylist, Map.of(
                "customerId", customerId, "appointmentId", apptId, "serviceId", serviceId,
                "treatmentDate", day.toString(), "details", "Layered cut, dry ends",
                "productsUsed", "Argan oil", "reactions", "None",
                "followUpServiceId", hairSpaId, "followUpAfterDays", 30)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.followUpDueDate").value(day.plusDays(30).toString())));
        send(post("/api/treatments"), stylist, Map.of(
                "customerId", customerId, "appointmentId", apptId, "serviceId", serviceId,
                "treatmentDate", day.toString(), "details", "again")).andExpect(status().isConflict());
        send(put("/api/treatments/" + treatmentId), admin, Map.of(
                "customerId", customerId, "serviceId", serviceId, "treatmentDate", day.toString(),
                "details", "Layered cut, dry ends, trimmed 2cm",
                "followUpServiceId", hairSpaId, "followUpAfterDays", 30)).andExpect(status().isOk());
        mvc.perform(get("/api/customers/" + customerId + "/treatments").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/recommendations/customer/" + customerId + "/suggestions")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].serviceName").value("Hair Spa"));

        // loyalty: visits are counted, rewards can be redeemed with points
        mvc.perform(get("/api/customers/" + customerId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedVisits").value(1))
                .andExpect(jsonPath("$.tier").value("REGULAR"));
        long rewardId = id(send(post("/api/rewards"), admin, Map.of("name", "Free blow-dry", "pointsCost", 5))
                .andExpect(status().isCreated()));
        send(post("/api/loyalty/customers/" + customerId + "/redeem-reward"), admin, Map.of("rewardId", rewardId))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanceAfter").value(0));
        send(post("/api/loyalty/customers/" + customerId + "/redeem-reward"), admin, Map.of("rewardId", rewardId))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/top-customers").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fullName").value("Maria Cruz"));
        mvc.perform(get("/api/settings/payment").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.qrPhAccountName").value("Jonathan Hair Studio"));
    }

    @Test
    void customerAppFlow() throws Exception {
        String admin = login("admin", "ChangeMe123!");
        long serviceId = id(send(post("/api/services"), admin, Map.of(
                "name", "Manicure", "price", 300, "durationMinutes", 45, "homeServiceAvailable", false))
                .andExpect(status().isCreated()));
        long stylistId = id(send(post("/api/stylists"), admin, Map.of(
                "fullName", "Marco Santos", "username", "marco", "password", "Stylist@123"))
                .andExpect(status().isCreated()));
        for (DayOfWeek day : DayOfWeek.values()) {
            send(post("/api/stylists/" + stylistId + "/schedules"), admin, Map.of(
                    "dayOfWeek", day.name(), "startTime", "09:00", "endTime", "18:00",
                    "homeServiceAvailable", false)).andExpect(status().isCreated());
        }

        // a walk-in customer already on file cannot be claimed by registering with their number
        send(post("/api/customers"), admin, Map.of("fullName", "Walk In", "phone", "0922 000 1111"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("fullName", "Someone Else", "phone", "09220001111", "password", "Password123"))))
                .andExpect(status().isConflict());

        // registration signs the customer in; the mobile number is the login
        String reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Lea Ramos", "phone", "+63 917 555 0100",
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.username").value("09175550100"))
                .andReturn().getResponse().getContentAsString();
        String customer = json.readTree(reg).get("token").asText();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("fullName", "Lea Again", "phone", "09175550100", "password", "Password123"))))
                .andExpect(status().isConflict());
        login("09175550100", "Password123");

        // a customer account can use the customer API but nothing from the back office
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Lea Ramos"));
        for (String path : new String[]{"/api/dashboard", "/api/customers", "/api/stylists", "/api/users",
                "/api/appointments", "/api/reports/summary", "/api/stylists/" + stylistId + "/schedules"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/stylists").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].username").doesNotExist())
                .andExpect(jsonPath("$[0].phone").doesNotExist());

        // booking from the app always waits for the salon's decision
        LocalDate day = LocalDate.now().plusDays(4);
        mvc.perform(get("/api/me/stylists/" + stylistId + "/availability?date=" + day + "&serviceId=" + serviceId)
                        .header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableStartTimes[0]").exists());
        Map<String, Object> booking = Map.of("stylistId", stylistId, "serviceId", serviceId, "type", "SALON",
                "startTime", day + "T11:00:00");
        long apptId = id(send(post("/api/me/appointments"), customer, booking)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")));
        send(post("/api/me/appointments"), customer, booking).andExpect(status().isConflict());

        // the salon accepts -> the customer is notified
        mvc.perform(put("/api/appointments/" + apptId + "/accept").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/notifications/unread-count").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].type").value("BOOKING_ACCEPTED"));
        mvc.perform(put("/api/me/notifications/seen-all").header("Authorization", "Bearer " + customer))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/notifications/unread-count").header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.unread").value(0));

        // moving a booking sends it back to PENDING; cancelling works; other customers can't touch it
        send(put("/api/me/appointments/" + apptId + "/reschedule"), customer, Map.of("startTime", day + "T14:00:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        String other = json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Other Person", "phone", "09175550200",
                                "password", "Password123")))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        mvc.perform(get("/api/me/appointments/" + apptId).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/me/appointments/" + apptId + "/cancel").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/me/appointments/" + apptId + "/cancel").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/me/appointments").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/me/loyalty").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0));
        mvc.perform(get("/api/me/recommendations").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk());

        // front desk can give an existing walk-in customer an app login
        long walkInId = id(send(post("/api/customers"), admin, Map.of("fullName", "Rosa Lim", "phone", "0933 444 5555"))
                .andExpect(status().isCreated()));
        send(post("/api/customers/" + walkInId + "/app-account"), admin, Map.of("password", "Welcome123"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("09334445555"));
        login("09334445555", "Welcome123");
        send(post("/api/customers/" + walkInId + "/app-account"), admin, Map.of("password", "Welcome123"))
                .andExpect(status().isConflict());
        send(put("/api/me/password"), customer, Map.of("currentPassword", "wrong-one", "newPassword", "Another1234"))
                .andExpect(status().isBadRequest());
        send(put("/api/me/password"), customer, Map.of("currentPassword", "Password123", "newPassword", "Another1234"))
                .andExpect(status().isNoContent());
        login("09175550100", "Another1234");
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
