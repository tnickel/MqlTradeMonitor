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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

import java.util.*;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
public class ApiControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountManager accountManager;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Test
    public void testGetAccountsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
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

    @Test
    public void testReceiveEaLogsLimitExceeded() throws Exception {
        String testApiKey = "test-api-key-xyz";
        UserEntity user = new UserEntity("testuser", "password", "ROLE_USER");
        user.setAllowedAccountIds(new HashSet<>(Arrays.asList(12345L)));
        user.setApiKey(testApiKey);

        when(userRepository.findByApiKey(testApiKey)).thenReturn(Optional.of(user));

        // Create a payload with 1001 entries
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            entries.add("Entry " + i);
        }
        
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"accountId\": 12345, \"logEntries\": [");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) jsonBuilder.append(",");
            jsonBuilder.append("\"").append(entries.get(i)).append("\"");
        }
        jsonBuilder.append("]}");

        mockMvc.perform(post("/api/ea-logs")
                .header("X-User-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBuilder.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testRegisterInvalidParameters() throws Exception {
        String testApiKey = "test-api-key-xyz";
        UserEntity user = new UserEntity("testuser", "password", "ROLE_USER");
        user.setAllowedAccountIds(new HashSet<>(Arrays.asList(12345L)));
        user.setApiKey(testApiKey);

        when(userRepository.findByApiKey(testApiKey)).thenReturn(Optional.of(user));

        // invalid accountId
        mockMvc.perform(post("/api/register")
                .header("X-User-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 0, \"broker\": \"ICMarkets\", \"currency\": \"USD\", \"balance\": 1000.0}"))
                .andExpect(status().isBadRequest());

        // invalid balance (NaN)
        mockMvc.perform(post("/api/register")
                .header("X-User-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 12345, \"broker\": \"ICMarkets\", \"currency\": \"USD\", \"balance\": \"NaN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testInitTradesLimitExceeded() throws Exception {
        String testApiKey = "test-api-key-xyz";
        UserEntity user = new UserEntity("testuser", "password", "ROLE_USER");
        user.setAllowedAccountIds(new HashSet<>(Arrays.asList(12345L)));
        user.setApiKey(testApiKey);

        when(userRepository.findByApiKey(testApiKey)).thenReturn(Optional.of(user));

        // Let's create an excessively large list of trades/closedTrades
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"accountId\": 12345, \"trades\": [], \"closedTrades\": [");
        for (int i = 0; i < 100005; i++) {
            if (i > 0) jsonBuilder.append(",");
            jsonBuilder.append("{\"ticket\": ").append(i).append("}");
        }
        jsonBuilder.append("]}");

        mockMvc.perform(post("/api/trades-init")
                .header("X-User-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBuilder.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetMarketRateSanitization() throws Exception {
        UserEntity testUser = new UserEntity("testuser", "password", "ROLE_USER");
        CustomUserDetails userDetails = new CustomUserDetails(testUser);

        // Invalid symbol (contains special characters)
        mockMvc.perform(get("/api/market/rate")
                .param("symbol", "EURUSD?param=1")
                .with(user(userDetails)))
                .andExpect(status().isBadRequest());

        // Too long symbol
        mockMvc.perform(get("/api/market/rate")
                .param("symbol", "A".repeat(21) )
                .with(user(userDetails)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testRegisterAnonymousDoesNotRedirect() throws Exception {
        mockMvc.perform(post("/api/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 12345, \"broker\": \"ICMarkets\"}"))
                .andExpect(status().isUnauthorized()); // Expect 401, not 302 redirect
    }

    @Test
    public void testHeartbeatAnonymousDoesNotRedirect() throws Exception {
        mockMvc.perform(post("/api/heartbeat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": 12345, \"version\": \"1.11\"}"))
                .andExpect(status().isUnauthorized()); // Expect 401, not 302 redirect
    }

    private CustomUserDetails regularUserWithAccounts(Long... accountIds) {
        UserEntity userEntity = new UserEntity("regular-user", "password", "ROLE_USER");
        userEntity.setId(42L);
        userEntity.setAllowedAccountIds(new HashSet<>(Arrays.asList(accountIds)));
        when(userRepository.findById(42L)).thenReturn(Optional.of(userEntity));
        return new CustomUserDetails(userEntity);
    }

    @Test
    public void testRegularUserCannotTriggerSirenOrModifyGlobalLayout() throws Exception {
        CustomUserDetails regular = regularUserWithAccounts(12345L);

        mockMvc.perform(post("/api/test-siren").with(user(regular)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/section/create")
                        .param("name", "Injected")
                        .with(user(regular)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/account/layout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"1\":[12345]}")
                        .with(user(regular)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testAccountMutationRequiresAccountPermission() throws Exception {
        CustomUserDetails regular = regularUserWithAccounts(12345L);

        mockMvc.perform(post("/api/account/meta-trader-info")
                        .param("accountId", "99999")
                        .param("metaTraderInfo", "unauthorized")
                        .with(user(regular)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/account/meta-trader-info")
                        .param("accountId", "12345")
                        .param("metaTraderInfo", "authorized")
                        .with(user(regular)))
                .andExpect(status().isOk());
    }

    @Test
    public void testDangerousDocumentUploadIsRejected() throws Exception {
        CustomUserDetails regular = regularUserWithAccounts(12345L);
        MockMultipartFile html = new MockMultipartFile(
                "file", "payload.html", "text/html", "<script>alert(1)</script>".getBytes());

        mockMvc.perform(multipart("/api/account/12345/documents")
                        .file(html)
                        .param("minText", "test")
                        .with(user(regular)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testRegularUserCannotImportSharedCsvAccount() throws Exception {
        CustomUserDetails regular = regularUserWithAccounts(12345L);
        MockMultipartFile csv = new MockMultipartFile(
                "file", "trades.csv", "text/csv", "Ticket,Symbol\n1,EURUSD".getBytes());

        mockMvc.perform(multipart("/api/trades/upload-csv")
                        .file(csv)
                        .param("name", "Shared")
                        .with(user(regular)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testConfiguredConcurrentSessionLimitIsApplied() {
        UserEntity entity = new UserEntity("session-limit-user", "password", "ROLE_USER");
        CustomUserDetails principal = new CustomUserDetails(entity);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setSession(new MockHttpSession());
            sessionAuthenticationStrategy.onAuthentication(
                    authentication, request, new MockHttpServletResponse());
        }

        assertEquals(3, sessionRegistry.getAllSessions(
                new CustomUserDetails(new UserEntity("session-limit-user", "ignored", "ROLE_USER")), false).size());
    }

    @Test
    public void testH2ConsoleIsHiddenWhenAdminSwitchIsOff() throws Exception {
        UserEntity admin = new UserEntity("h2-admin", "password", "ROLE_ADMIN");
        mockMvc.perform(get("/h2-console").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isNotFound());
    }
}
