/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.security.token;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.testutils.ManuallyTriggeredScheduledExecutorService;
import org.apache.flink.util.concurrent.ManuallyTriggeredScheduledExecutor;

import org.apache.hadoop.security.UserGroupInformation;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test for {@link DelegationTokenManager}. */
public class KerberosDelegationTokenManagerTest {

    @Test
    public void isProviderEnabledMustGiveBackTrueByDefault() {
        ExceptionThrowingDelegationTokenProvider.enabled = false;
        Configuration configuration = new Configuration();
        KerberosDelegationTokenManager delegationTokenManager =
                new KerberosDelegationTokenManager(configuration, null, null);

        assertTrue(delegationTokenManager.isProviderEnabled("test"));
    }

    @Test
    public void isProviderEnabledMustGiveBackFalseWhenDisabled() {
        ExceptionThrowingDelegationTokenProvider.enabled = false;
        Configuration configuration = new Configuration();
        configuration.setBoolean("security.kerberos.token.provider.test.enabled", false);
        KerberosDelegationTokenManager delegationTokenManager =
                new KerberosDelegationTokenManager(configuration, null, null);

        assertFalse(delegationTokenManager.isProviderEnabled("test"));
    }

    @Test
    public void configurationIsNullMustFailFast() {
        assertThrows(Exception.class, () -> new KerberosDelegationTokenManager(null, null, null));
    }

    @Test
    public void oneProviderThrowsExceptionMustFailFast() {
        assertThrows(
                Exception.class,
                () -> {
                    try {
                        ExceptionThrowingDelegationTokenProvider.enabled = true;
                        new KerberosDelegationTokenManager(new Configuration(), null, null);
                    } finally {
                        ExceptionThrowingDelegationTokenProvider.enabled = false;
                    }
                });
    }

    @Test
    public void testAllProvidersLoaded() {
        ExceptionThrowingDelegationTokenProvider.enabled = false;
        ExceptionThrowingDelegationTokenProvider.constructed = false;
        Configuration configuration = new Configuration();
        configuration.setBoolean("security.kerberos.token.provider.throw.enabled", false);
        KerberosDelegationTokenManager delegationTokenManager =
                new KerberosDelegationTokenManager(configuration, null, null);

        assertEquals(1, delegationTokenManager.delegationTokenProviders.size());
        assertTrue(delegationTokenManager.isProviderLoaded("test"));
        assertTrue(ExceptionThrowingDelegationTokenProvider.constructed);
        assertFalse(delegationTokenManager.isProviderLoaded("throw"));
    }

    @Test
    public void testStartTGTRenewalShouldScheduleRenewal() throws IOException {
        final ManuallyTriggeredScheduledExecutor scheduledExecutor =
                new ManuallyTriggeredScheduledExecutor();
        final ManuallyTriggeredScheduledExecutorService scheduler =
                new ManuallyTriggeredScheduledExecutorService();
        try (MockedStatic<UserGroupInformation> ugi = mockStatic(UserGroupInformation.class)) {
            UserGroupInformation userGroupInformation = mock(UserGroupInformation.class);
            when(userGroupInformation.isFromKeytab()).thenReturn(true);
            ugi.when(UserGroupInformation::getCurrentUser).thenReturn(userGroupInformation);

            ExceptionThrowingDelegationTokenProvider.enabled = false;
            ExceptionThrowingDelegationTokenProvider.constructed = false;
            Configuration configuration = new Configuration();
            configuration.setBoolean("security.kerberos.token.provider.throw.enabled", false);
            KerberosDelegationTokenManager delegationTokenManager =
                    new KerberosDelegationTokenManager(configuration, scheduledExecutor, scheduler);

            delegationTokenManager.startTGTRenewal();
            scheduledExecutor.triggerPeriodicScheduledTasks();
            scheduler.triggerAll();
            delegationTokenManager.stopTGTRenewal();

            verify(userGroupInformation, times(1)).checkTGTAndReloginFromKeytab();
        }
    }
}
