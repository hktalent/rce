/*
 * Copyright (C) 2006-2016 DLR, Germany
 * 
 * All rights reserved
 * 
 * http://www.rcenvironment.de/
 */

package de.rcenvironment.core.component.update.internal;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.rcenvironment.core.communication.api.CommunicationService;
import de.rcenvironment.core.communication.common.LogicalNodeId;
import de.rcenvironment.core.component.update.api.DistributedPersistentComponentDescriptionUpdateService;
import de.rcenvironment.core.component.update.api.PersistentComponentDescription;
import de.rcenvironment.core.component.update.api.PersistentDescriptionFormatVersion;
import de.rcenvironment.core.component.update.api.RemotablePersistentComponentDescriptionUpdateService;
import de.rcenvironment.core.toolkitbridge.transitional.ConcurrencyUtils;
import de.rcenvironment.core.utils.common.StringUtils;
import de.rcenvironment.core.utils.common.rpc.RemoteOperationException;
import de.rcenvironment.toolkit.modules.concurrency.api.AsyncExceptionListener;
import de.rcenvironment.toolkit.modules.concurrency.api.CallablesGroup;
import de.rcenvironment.toolkit.modules.concurrency.api.TaskDescription;

/**
 * Implementation of {@link DistributedPersistentComponentDescriptionUpdateService}.
 * 
 * @author Doreen Seider
 */
public class DistributedPersistentComponentDescriptionUpdateServiceImpl implements DistributedPersistentComponentDescriptionUpdateService {

    private static final Log LOGGER = LogFactory.getLog(DistributedPersistentComponentDescriptionUpdateServiceImpl.class);

    private CommunicationService communicationService;

    @Override
    public int getFormatVersionsAffectedByUpdate(List<PersistentComponentDescription> descriptions, final boolean silent) {

        int versionsToUpdate = PersistentDescriptionFormatVersion.NONE;

        CallablesGroup<Integer> callablesGroup = ConcurrencyUtils.getFactory().createCallablesGroup(Integer.class);

        final Map<LogicalNodeId, List<PersistentComponentDescription>> sortedDescriptionsMap =
            Collections.unmodifiableMap(sortPersistentComponentDescriptions(descriptions));

        for (LogicalNodeId node : sortedDescriptionsMap.keySet()) {
            final LogicalNodeId node2 = node;
            callablesGroup.add(new Callable<Integer>() {

                @Override
                @TaskDescription("Distributed persistent component update check: getFormatVersionsAffectedByUpdate()")
                public Integer call() throws Exception {
                    try {
                        RemotablePersistentComponentDescriptionUpdateService udpateService = communicationService
                            .getRemotableService(RemotablePersistentComponentDescriptionUpdateService.class, node2);
                        return udpateService.getFormatVersionsAffectedByUpdate(sortedDescriptionsMap.get(node2), silent);
                    } catch (RemoteOperationException | RuntimeException e) {
                        LOGGER.warn(StringUtils.format("Failed to check for persistent component updates for node: %s; cause: %s",
                            node2, e.toString()));
                        return null;
                    }

                }
            });

            List<Integer> results = callablesGroup.executeParallel(new AsyncExceptionListener() {

                @Override
                public void onAsyncException(Exception e) {
                    LOGGER.warn("Exception during asynchrous execution", e);
                }
            });
            // merge results
            for (Integer singleResult : results) {
                if (singleResult != null) {
                    versionsToUpdate = versionsToUpdate | singleResult;
                }
            }
        }
        return versionsToUpdate;
    }

    @Override
    public List<PersistentComponentDescription> performComponentDescriptionUpdates(final int formatVersion,
        List<PersistentComponentDescription> descriptions, final boolean silent) throws IOException {

        List<PersistentComponentDescription> allUpdatedDescriptions = new ArrayList<PersistentComponentDescription>();

        final Map<LogicalNodeId, List<PersistentComponentDescription>> sortedDescriptionsMap =
            Collections.unmodifiableMap(sortPersistentComponentDescriptions(descriptions));

        final List<PersistentComponentDescription> unModdescriptions = Collections.unmodifiableList(descriptions);

        CallablesGroup<List> callablesGroup = ConcurrencyUtils.getFactory().createCallablesGroup(List.class);

        for (LogicalNodeId node : sortedDescriptionsMap.keySet()) {
            final LogicalNodeId node2 = node;
            callablesGroup.add(new Callable<List>() {

                @Override
                @TaskDescription("Distributed persistent component update: performComponentDescriptionUpdates()")
                public List call() throws Exception {
                    RemotablePersistentComponentDescriptionUpdateService updateService = communicationService
                        .getRemotableService(RemotablePersistentComponentDescriptionUpdateService.class, node2);
                    if ((updateService.getFormatVersionsAffectedByUpdate(unModdescriptions, silent) & formatVersion) == formatVersion) {
                        try {
                            return updateService
                                .performComponentDescriptionUpdates(formatVersion, sortedDescriptionsMap.get(node2), silent);
                        } catch (UndeclaredThrowableException e) {
                            LOGGER.warn("Failed to perform persistent component updates for node: " + node2, e);
                            return null;
                        }
                    } else {
                        return sortedDescriptionsMap.get(node2);
                    }
                }
            });
        }

        List<List> results = callablesGroup.executeParallel(new AsyncExceptionListener() {

            @Override
            public void onAsyncException(Exception e) {
                LOGGER.warn("Exception during asynchrous execution", e);
            }
        });

        // merge results
        for (List singleResult : results) {
            if (singleResult != null) {
                allUpdatedDescriptions.addAll(singleResult);
            }
        }
        return allUpdatedDescriptions;
    }

    protected void bindCommunicationService(CommunicationService newCommunicationService) {
        this.communicationService = newCommunicationService;
    }

    private Map<LogicalNodeId, List<PersistentComponentDescription>> sortPersistentComponentDescriptions(
        List<PersistentComponentDescription> descriptions) {

        Map<LogicalNodeId, List<PersistentComponentDescription>> sortedDescriptions =
            new HashMap<LogicalNodeId, List<PersistentComponentDescription>>();

        for (PersistentComponentDescription description : descriptions) {
            if (!sortedDescriptions.containsKey(description.getComponentNodeIdentifier())) {
                sortedDescriptions.put(description.getComponentNodeIdentifier(), new ArrayList<PersistentComponentDescription>());
            }
            sortedDescriptions.get(description.getComponentNodeIdentifier()).add(description);
        }
        return sortedDescriptions;
    }

}
