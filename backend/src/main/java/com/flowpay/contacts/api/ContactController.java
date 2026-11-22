package com.flowpay.contacts.api;

import com.flowpay.contacts.application.ContactService;
import com.flowpay.contacts.application.ContactService.ContactInput;
import com.flowpay.contacts.application.ContactService.ContactPage;
import com.flowpay.contacts.application.ContactService.ContactRole;
import com.flowpay.contacts.application.ContactService.ContactView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/contacts")
public class ContactController {
    private final ContactService contacts;

    public ContactController(ContactService contacts) { this.contacts = contacts; }

    @GetMapping
    ContactPage list(@AuthenticationPrincipal UserDetails user,
                     @RequestParam(required = false) @Size(max = 100) String q,
                     @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return contacts.search(user.getUsername(), q, page, size);
    }

    @PostMapping
    ResponseEntity<ContactView> create(@AuthenticationPrincipal UserDetails user,
                                       @Valid @RequestBody ContactRequest request) {
        var contact = contacts.create(user.getUsername(), request.toInput());
        return ResponseEntity.created(URI.create("/api/contacts/" + contact.id())).body(contact);
    }

    @GetMapping("/{id}")
    ContactView get(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id) {
        return contacts.get(user.getUsername(), id);
    }

    @PutMapping("/{id}")
    ContactView update(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id,
                       @Valid @RequestBody ContactRequest request) {
        return contacts.update(user.getUsername(), id, request.toInput());
    }

    record ContactRequest(@NotBlank @Size(max = 160) String name,
                          @NotEmpty Set<ContactRole> roles,
                          @Email @Size(max = 254) String email,
                          @Size(max = 32) String phone,
                          @Size(max = 1000) String notes) {
        ContactInput toInput() { return new ContactInput(name, roles, email, phone, notes); }
        @Override public String toString() { return "ContactRequest[REDACTED]"; }
    }
}
