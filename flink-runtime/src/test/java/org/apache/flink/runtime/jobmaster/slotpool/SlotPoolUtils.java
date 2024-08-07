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

package org.apache.flink.runtime.jobmaster.slotpool;

import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.executiongraph.utils.SimpleAckingTaskManagerGateway;
import org.apache.flink.runtime.jobmanager.slots.TaskManagerGateway;
import org.apache.flink.runtime.jobmaster.SlotRequestId;
import org.apache.flink.runtime.slots.ResourceRequirement;
import org.apache.flink.runtime.taskexecutor.slot.SlotOffer;
import org.apache.flink.runtime.taskmanager.LocalTaskManagerLocation;
import org.apache.flink.runtime.taskmanager.TaskManagerLocation;
import org.apache.flink.runtime.util.ResourceCounter;
import org.apache.flink.util.FlinkException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.reducing;
import static org.assertj.core.api.Assertions.assertThat;

/** Testing utility functions for the {@link SlotPool}. */
public class SlotPoolUtils {

    public static final Duration TIMEOUT = Duration.ofSeconds(10L);

    private SlotPoolUtils() {
        throw new UnsupportedOperationException("Cannot instantiate this class.");
    }

    public static DeclarativeSlotPoolBridge createDeclarativeSlotPoolBridge() {
        return new DeclarativeSlotPoolBridgeBuilder().build();
    }

    public static CompletableFuture<PhysicalSlot> requestNewAllocatedBatchSlot(
            SlotPool slotPool,
            ComponentMainThreadExecutor mainThreadExecutor,
            ResourceProfile resourceProfile) {

        return CompletableFuture.supplyAsync(
                        () ->
                                slotPool.requestNewAllocatedBatchSlot(
                                        new SlotRequestId(), resourceProfile),
                        mainThreadExecutor)
                .thenCompose(Function.identity());
    }

    public static ResourceID offerSlots(
            SlotPool slotPool,
            ComponentMainThreadExecutor mainThreadExecutor,
            List<ResourceProfile> resourceProfiles) {
        return offerSlots(
                slotPool,
                mainThreadExecutor,
                resourceProfiles,
                new SimpleAckingTaskManagerGateway());
    }

    public static ResourceID tryOfferSlots(
            SlotPool slotPool,
            ComponentMainThreadExecutor mainThreadExecutor,
            List<ResourceProfile> resourceProfiles) {
        return offerSlots(
                slotPool,
                mainThreadExecutor,
                resourceProfiles,
                new SimpleAckingTaskManagerGateway(),
                false);
    }

    public static ResourceID offerSlots(
            SlotPool slotPool,
            ComponentMainThreadExecutor mainThreadExecutor,
            List<ResourceProfile> resourceProfiles,
            TaskManagerGateway taskManagerGateway) {
        return offerSlots(slotPool, mainThreadExecutor, resourceProfiles, taskManagerGateway, true);
    }

    private static ResourceID offerSlots(
            SlotPool slotPool,
            ComponentMainThreadExecutor mainThreadExecutor,
            List<ResourceProfile> resourceProfiles,
            TaskManagerGateway taskManagerGateway,
            boolean assertAllSlotsAreAccepted) {
        final TaskManagerLocation taskManagerLocation = new LocalTaskManagerLocation();
        CompletableFuture.runAsync(
                        () -> {
                            slotPool.registerTaskManager(taskManagerLocation.getResourceID());

                            final Collection<SlotOffer> slotOffers =
                                    IntStream.range(0, resourceProfiles.size())
                                            .mapToObj(
                                                    i ->
                                                            new SlotOffer(
                                                                    new AllocationID(),
                                                                    i,
                                                                    resourceProfiles.get(i)))
                                            .collect(Collectors.toList());

                            final Collection<SlotOffer> acceptedOffers =
                                    slotPool.offerSlots(
                                            taskManagerLocation, taskManagerGateway, slotOffers);

                            if (assertAllSlotsAreAccepted) {
                                assertThat(acceptedOffers).isEqualTo(slotOffers);
                            }
                        },
                        mainThreadExecutor)
                .join();

        return taskManagerLocation.getResourceID();
    }

    public static void releaseTaskManager(
            SlotPool slotPool,
            ComponentMainThreadExecutor mainThreadExecutor,
            ResourceID taskManagerResourceId) {
        CompletableFuture.runAsync(
                        () ->
                                slotPool.releaseTaskManager(
                                        taskManagerResourceId,
                                        new FlinkException("Let's get rid of the offered slot.")),
                        mainThreadExecutor)
                .join();
    }

    public static void notifyNotEnoughResourcesAvailable(
            SlotPoolService slotPoolService,
            ComponentMainThreadExecutor mainThreadExecutor,
            Collection<ResourceRequirement> acquiredResources) {
        CompletableFuture.runAsync(
                        () -> slotPoolService.notifyNotEnoughResourcesAvailable(acquiredResources),
                        mainThreadExecutor)
                .join();
    }

    static ResourceCounter calculateResourceCounter(ResourceProfile[] resourceProfiles) {
        final Map<ResourceProfile, Integer> resources =
                Arrays.stream(resourceProfiles)
                        .collect(
                                Collectors.groupingBy(
                                        Function.identity(), reducing(0, e -> 1, Integer::sum)));
        final ResourceCounter increment = ResourceCounter.withResources(resources);
        return increment;
    }
}
