package com.styletrack.service;

import com.styletrack.dto.AppointmentDto;
import com.styletrack.dto.CustomerDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.entity.Customer;
import com.styletrack.entity.User;
import com.styletrack.entity.enums.Role;
import com.styletrack.exception.BusinessException;
import com.styletrack.repository.UserRepository;
import com.styletrack.util.PhoneUtil;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final LoyaltyService loyaltyService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<CustomerDto.Response> search(String q, Pageable pageable) {
        String term = q == null ? "" : q.trim();
        return PageResponse.of(customerRepository.search(term, pageable).map(this::toResponse));
    }

    public CustomerDto.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public CustomerDto.Response create(CustomerDto.Request r) {
        Customer customer = Customer.builder().build();
        apply(customer, r);
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerDto.Response update(Long id, CustomerDto.Request r) {
        Customer customer = find(id);
        apply(customer, r);
        return toResponse(customer);
    }

    /** Customers are never hard-deleted so their history and loyalty ledger stay intact. */
    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    @Transactional
    public CustomerDto.Response reactivate(Long id) {
        Customer customer = find(id);
        customer.setActive(true);
        return toResponse(customer);
    }

    /** Full booking history for a customer (all statuses, newest first). */
    public PageResponse<AppointmentDto.Response> history(Long customerId, Pageable pageable) {
        find(customerId);
        return PageResponse.of(appointmentRepository.findByCustomerId(customerId, pageable)
                .map(AppointmentDto.Response::from));
    }

    /** Gives an existing customer a mobile-app login (their mobile number) with a starting password. */
    @Transactional
    public CustomerDto.AppAccountResponse createAppAccount(Long id, CustomerDto.AppAccountRequest r) {
        Customer customer = find(id);
        if (customer.getUser() != null) {
            throw new BusinessException("This customer already has an app login", HttpStatus.CONFLICT);
        }
        String phone = PhoneUtil.normalize(customer.getPhone());
        if (!PhoneUtil.isValid(phone)) {
            throw new BusinessException("Add a valid mobile number to the customer first");
        }
        if (userRepository.existsByUsername(phone) || customerRepository.existsByPhoneAndUserIsNotNull(phone)) {
            throw new BusinessException("Another account already uses this mobile number", HttpStatus.CONFLICT);
        }
        User user = userRepository.save(User.builder()
                .username(phone)
                .password(passwordEncoder.encode(r.getPassword()))
                .fullName(customer.getFullName())
                .email(customer.getEmail())
                .phone(phone)
                .role(Role.CUSTOMER)
                .build());
        customer.setPhone(phone);
        customer.setUser(user);
        return CustomerDto.AppAccountResponse.builder().username(phone).build();
    }

    private CustomerDto.Response toResponse(Customer c) {
        CustomerDto.Response response = CustomerDto.Response.from(c);
        response.setTier(loyaltyService.tierFor(c.getCompletedVisits()));
        return response;
    }

    private Customer find(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private void apply(Customer c, CustomerDto.Request r) {
        c.setFullName(r.getFullName().trim());
        String phone = PhoneUtil.normalize(r.getPhone());
        // customers with an app login sign in with their number, so it cannot be changed from here
        if (c.getUser() == null) {
            c.setPhone(PhoneUtil.isValid(phone) ? phone : r.getPhone().trim());
        }
        c.setEmail(r.getEmail());
        c.setAddress(r.getAddress());
        c.setBirthDate(r.getBirthDate());
        c.setNotes(r.getNotes());
    }
}
