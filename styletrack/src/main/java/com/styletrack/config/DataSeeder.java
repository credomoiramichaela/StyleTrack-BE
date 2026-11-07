package com.styletrack.config;

import com.styletrack.entity.Customer;
import com.styletrack.entity.Reward;
import com.styletrack.entity.ServiceItem;
import com.styletrack.entity.Stylist;
import com.styletrack.entity.StylistSchedule;
import com.styletrack.entity.User;
import com.styletrack.entity.enums.Role;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.RewardRepository;
import com.styletrack.repository.ServiceItemRepository;
import com.styletrack.repository.StylistRepository;
import com.styletrack.repository.StylistScheduleRepository;
import com.styletrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Creates the first owner/admin account on an empty database. When SEED_DEMO=true it also loads
 * demo staff, stylists, services, schedules and customers (demo passwords are in the README), plus sample
 * GCash / QR Ph payments in every state (see {@link DemoPaymentSeeder}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final StylistRepository stylistRepository;
    private final StylistScheduleRepository scheduleRepository;
    private final ServiceItemRepository serviceRepository;
    private final CustomerRepository customerRepository;
    private final RewardRepository rewardRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoPaymentSeeder demoPaymentSeeder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Seed seed = appProperties.getSeed();
        if (!userRepository.existsByUsername(seed.getAdminUsername())) {
            userRepository.save(newUser(seed.getAdminUsername(), seed.getAdminPassword(), "Owner", Role.SUPER_ADMIN));
            log.warn("Created initial admin account '{}'. Change its password after first login.",
                    seed.getAdminUsername());
        }
        if (seed.isDemo() && serviceRepository.count() == 0) {
            seedDemo();
        }
        if (seed.isDemo()) {
            demoPaymentSeeder.seed();
        }
    }

    private void seedDemo() {
        userRepository.save(newUser("staff1", "Staff@12345", "Front Desk Staff", Role.ADMIN));

        Stylist anna = createStylist("Anna Reyes", "Hair color & cuts", "stylist1", "Stylist@12345");
        Stylist marco = createStylist("Marco Santos", "Nails & spa", "stylist2", "Stylist@12345");
        for (Stylist s : new Stylist[]{anna, marco}) {
            for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY}) {
                scheduleRepository.save(StylistSchedule.builder().stylist(s).dayOfWeek(day)
                        .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0))
                        .homeServiceAvailable(day != DayOfWeek.SATURDAY).build());
            }
        }

        service("Haircut", "Hair", "350", 30, false);
        service("Hair Color", "Hair", "1500", 90, false);
        service("Hair Spa", "Hair", "800", 60, true);
        service("Manicure", "Nails", "250", 45, true);
        service("Pedicure", "Nails", "300", 45, true);

        rewardRepository.save(Reward.builder().name("Free blow-dry").description("Complimentary blow-dry")
                .pointsCost(10).build());
        rewardRepository.save(Reward.builder().name("Free manicure").description("Complimentary classic manicure")
                .pointsCost(25).build());

        customerRepository.save(Customer.builder().fullName("Maria Cruz").phone("09171234567")
                .email("maria@example.com").address("12 Rizal St, Quezon City").build());
        customerRepository.save(Customer.builder().fullName("Juan Dela Cruz").phone("09181234567")
                .address("45 Mabini Ave, Makati").build());
        log.info("Demo data loaded (staff1, stylist1, stylist2).");
    }

    private Stylist createStylist(String name, String specialization, String username, String password) {
        User user = userRepository.save(newUser(username, password, name, Role.STYLIST));
        return stylistRepository.save(Stylist.builder().user(user).fullName(name)
                .specialization(specialization).build());
    }

    private void service(String name, String category, String price, int minutes, boolean home) {
        serviceRepository.save(ServiceItem.builder().name(name).category(category)
                .price(new BigDecimal(price)).durationMinutes(minutes).homeServiceAvailable(home).build());
    }

    private User newUser(String username, String rawPassword, String fullName, Role role) {
        return User.builder().username(username).password(passwordEncoder.encode(rawPassword))
                .fullName(fullName).role(role).build();
    }
}
