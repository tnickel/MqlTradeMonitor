package de.trademonitor.controller;

import de.trademonitor.entity.UserEntity;
import de.trademonitor.repository.UserRepository;
import de.trademonitor.security.CustomUserDetails;
import de.trademonitor.service.AccountManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ApiControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountManager accountManager;

    @MockBean
    private UserRepository userRepository;

    @Test
    public void testGetAccountsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().is3xxRedirection()); // redirects to login since it requires authentication
    }

    @Test
    public void testGetAccountsAsAdmin() throws Exception {
        UserEntity admin = new UserEntity("admin", "password", "ROLE_ADMIN");
        CustomUserDetails userDetails = new CustomUserDetails(admin);

        List<Map<String, Object>> mockAccounts = new ArrayList<>();
        Map<String, Object> acc1 = new HashMap<>();
        acc1.put("accountId", 12345L);
        acc1.put("broker", "ICMarkets");
        mockAccounts.add(acc1);

        Map<String, Object> acc2 = new HashMap<>();
        acc2.put("accountId", 67890L);
        acc2.put("broker", "Tickmill");
        mockAccounts.add(acc2);

        when(accountManager.getAccountsWithStatus()).thenReturn(mockAccounts);

        mockMvc.perform(get("/api/accounts")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].accountId").value(12345))
                .andExpect(jsonPath("$[1].accountId").value(67890));
    }

    @Test
    public void testGetAccountsAsDemoUserFiltered() throws Exception {
        UserEntity demoUser = new UserEntity("demouser", "password", "ROLE_DEMO");
        demoUser.setAllowedAccountIds(new HashSet<>(Arrays.asList(12345L)));
        CustomUserDetails userDetails = new CustomUserDetails(demoUser);

        List<Map<String, Object>> mockAccounts = new ArrayList<>();
        Map<String, Object> acc1 = new HashMap<>();
        acc1.put("accountId", 12345L);
        acc1.put("broker", "ICMarkets");
        mockAccounts.add(acc1);

        Map<String, Object> acc2 = new HashMap<>();
        acc2.put("accountId", 67890L);
        acc2.put("broker", "Tickmill");
        mockAccounts.add(acc2);

        when(accountManager.getAccountsWithStatus()).thenReturn(mockAccounts);

        mockMvc.perform(get("/api/accounts")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value(12345));
    }

    @Test
    public void testReceiveEaLogsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/ea-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 12345, \"logEntries\": [\"Test entry\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testReceiveEaLogsAuthorized() throws Exception {
        String testApiKey = "test-api-key-xyz";
        UserEntity user = new UserEntity("testuser", "password", "ROLE_USER");
        user.setAllowedAccountIds(new HashSet<>(Arrays.asList(12345L)));
        user.setApiKey(testApiKey);

        when(userRepository.findByApiKey(testApiKey)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/ea-logs")
                .header("X-User-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 12345, \"logEntries\": [\"Test entry\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testReceiveEaLogsUnauthorizedWithWrongKey() throws Exception {
        String testApiKey = "test-api-key-xyz";
        UserEntity user = new UserEntity("testuser", "password", "ROLE_USER");
        user.setAllowedAccountIds(new HashSet<>(Arrays.asList(99999L))); // Not 12345L
        user.setApiKey(testApiKey);

        when(userRepository.findByApiKey(testApiKey)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/ea-logs")
                .header("X-User-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 12345, \"logEntries\": [\"Test entry\"]}"))
                .andExpect(status().isUnauthorized());
    }
}
