package org.artifactory.security;


import com.google.common.cache.Cache;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.storage.security.service.UserLockInMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mockitoSession;
import static org.mockito.Mockito.verify;

class SecurityServiceImplJUnit5Test {

    @Mock
    private UserLockInMemoryService userLockInMemoryService;
    @Mock
    private UserGroupStoreService userGroupStoreService;
    @Mock
    private Cache<String, List<Long>> unknownUsersCache;

    private MockitoSession mockitoSession;
    @InjectMocks
    private SecurityServiceImpl securityService;

    @BeforeEach
    void setUp() {
        mockitoSession = mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
        ReflectionTestUtils.setField(securityService, "unknownUsersCache", unknownUsersCache);
    }

    @AfterEach
    void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    void testInterceptLoginSuccessUnknownUser() {
        securityService.interceptLoginSuccess("user");
        verify(userLockInMemoryService).resetIncorrectLoginAttempts("user");
        verify(unknownUsersCache).invalidate("user");
    }
}