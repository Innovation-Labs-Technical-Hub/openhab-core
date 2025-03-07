/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.semantics.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.core.common.registry.ProviderChangeListener;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.Metadata;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.semantics.ManagedSemanticTagProvider;
import org.openhab.core.semantics.SemanticTagRegistry;
import org.openhab.core.semantics.model.DefaultSemanticTagProvider;
import org.openhab.core.test.java.JavaTest;

/**
 * @author Simon Lamon - Initial contribution
 */
@NonNullByDefault
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SemanticsMetadataProviderTest extends JavaTest {

    private static final String ITEM_NAME = "switchItem";

    private static final String GROUP_ITEM_NAME = "groupItem";

    private @Mock @NonNullByDefault({}) ItemRegistry itemRegistry;
    private @Mock @NonNullByDefault({}) ProviderChangeListener<@NonNull Metadata> changeListener;
    private @Mock @NonNullByDefault({}) ManagedSemanticTagProvider managedSemanticTagProviderMock;

    private @NonNullByDefault({}) SemanticsMetadataProvider semanticsMetadataProvider;

    @BeforeEach
    public void beforeEach() throws Exception {
        setupInterceptedLogger(SemanticsMetadataProvider.class, LogLevel.DEBUG);

        when(managedSemanticTagProviderMock.getAll()).thenReturn(List.of());
        SemanticTagRegistry semanticTagRegistry = new SemanticTagRegistryImpl(new DefaultSemanticTagProvider(),
                managedSemanticTagProviderMock);

        semanticsMetadataProvider = new SemanticsMetadataProvider(itemRegistry, semanticTagRegistry) {
            {
                addProviderChangeListener(changeListener);
            }
        };

        semanticsMetadataProvider.activate();
    }

    private void assertCorrectAddedEvents(Metadata expected) {
        ArgumentCaptor<Metadata> eventCaptor = ArgumentCaptor.forClass(Metadata.class);
        verify(changeListener, atLeastOnce()).added(eq(semanticsMetadataProvider), eventCaptor.capture());
        Metadata event = eventCaptor.getAllValues().stream().findFirst().get();
        assertEquals(expected, event);
    }

    private void assertCorrectUpdatedEvents(Metadata oldExpected, Metadata expected) {
        ArgumentCaptor<Metadata> eventCaptor = ArgumentCaptor.forClass(Metadata.class);
        ArgumentCaptor<Metadata> oldEventCaptor = ArgumentCaptor.forClass(Metadata.class);
        verify(changeListener, atLeastOnce()).updated(eq(semanticsMetadataProvider), oldEventCaptor.capture(),
                eventCaptor.capture());
        Metadata oldEvent = oldEventCaptor.getAllValues().stream().findFirst().get();
        Metadata event = eventCaptor.getAllValues().stream().findFirst().get();
        assertEquals(oldExpected, oldEvent);
        assertEquals(expected, event);
    }

    private void assertCorrectRemoveEvents(Metadata expected) {
        ArgumentCaptor<Metadata> eventCaptor = ArgumentCaptor.forClass(Metadata.class);
        verify(changeListener, atLeastOnce()).removed(eq(semanticsMetadataProvider), eventCaptor.capture());
        Metadata event = eventCaptor.getAllValues().stream().findFirst().get();
        assertEquals(expected, event);
    }

    @Test
    public void testItemAdded() {
        GenericItem item = new SwitchItem(ITEM_NAME);
        item.addTag("Door");
        semanticsMetadataProvider.added(item);

        Metadata metadata = Objects.requireNonNull(getMetadata(item));
        assertEquals("Equipment_Door", metadata.getValue());

        assertCorrectAddedEvents(metadata);
    }

    @Test
    public void testItemUpdatedToAnotherTag() {
        GenericItem oldItem = new SwitchItem(ITEM_NAME);
        oldItem.addTag("Door");
        semanticsMetadataProvider.added(oldItem);

        Metadata oldMetadata = Objects.requireNonNull(getMetadata(oldItem));

        GenericItem newItem = new SwitchItem(ITEM_NAME);
        newItem.addTag("Indoor");
        semanticsMetadataProvider.updated(oldItem, newItem);

        Metadata metadata = Objects.requireNonNull(getMetadata(newItem));
        assertEquals("Location_Indoor", metadata.getValue());

        assertCorrectUpdatedEvents(oldMetadata, metadata);
    }

    @Test
    public void testItemUpdatedToNoTag() {
        GenericItem oldItem = new SwitchItem(ITEM_NAME);
        oldItem.addTag("Door");
        semanticsMetadataProvider.added(oldItem);

        Metadata oldMetadata = Objects.requireNonNull(getMetadata(oldItem));

        GenericItem newItem = new SwitchItem(ITEM_NAME);
        semanticsMetadataProvider.updated(oldItem, newItem);

        Metadata metadata = getMetadata(newItem);
        assertNull(metadata);

        assertCorrectRemoveEvents(oldMetadata);
    }

    @Test
    public void testItemRemoved() {
        GenericItem item = new SwitchItem(ITEM_NAME);
        item.addTag("Door");
        semanticsMetadataProvider.added(item);

        Metadata oldMetadata = Objects.requireNonNull(getMetadata(item));

        semanticsMetadataProvider.removed(item);

        Metadata metadata = getMetadata(item);
        assertNull(metadata);

        assertCorrectRemoveEvents(oldMetadata);
    }

    @Test
    public void testGroupItemAddedBeforeMemberItemAdded() {
        GenericItem item = new SwitchItem(ITEM_NAME);
        item.addTag("Door");
        item.addGroupName(GROUP_ITEM_NAME);

        GroupItem groupItem = new GroupItem(GROUP_ITEM_NAME);
        groupItem.addMember(item);
        groupItem.addTag("LivingRoom");

        when(itemRegistry.get(GROUP_ITEM_NAME)).thenReturn(groupItem);

        semanticsMetadataProvider.added(groupItem);
        semanticsMetadataProvider.added(item);

        Metadata metadata = Objects.requireNonNull(getMetadata(item));
        assertEquals("Equipment_Door", metadata.getValue());
        assertEquals(GROUP_ITEM_NAME, metadata.getConfiguration().get("hasLocation"));
    }

    @Test
    public void testGroupItemAddedAfterMemberItemAdded() {
        GenericItem item = new SwitchItem(ITEM_NAME);
        item.addTag("Door");
        item.addGroupName(GROUP_ITEM_NAME);

        GroupItem groupItem = new GroupItem(GROUP_ITEM_NAME);
        groupItem.addMember(item);
        groupItem.addTag("LivingRoom");

        semanticsMetadataProvider.added(item);

        Metadata oldMetadata = Objects.requireNonNull(getMetadata(item));
        assertEquals("Equipment_Door", oldMetadata.getValue());
        assertNull(oldMetadata.getConfiguration().get("hasLocation"));

        when(itemRegistry.get(GROUP_ITEM_NAME)).thenReturn(groupItem);

        semanticsMetadataProvider.added(groupItem);

        Metadata metadata = Objects.requireNonNull(getMetadata(item));
        assertEquals("Equipment_Door", oldMetadata.getValue());
        assertEquals(GROUP_ITEM_NAME, metadata.getConfiguration().get("hasLocation"));
    }

    @Test
    public void testGroupItemRemovedAfterMemberItemAdded() {
        GenericItem item = new SwitchItem(ITEM_NAME);
        item.addTag("Door");
        item.addGroupName(GROUP_ITEM_NAME);

        GroupItem groupItem = new GroupItem(GROUP_ITEM_NAME);
        groupItem.addMember(item);
        groupItem.addTag("LivingRoom");

        when(itemRegistry.get(GROUP_ITEM_NAME)).thenReturn(groupItem);

        semanticsMetadataProvider.added(groupItem);
        semanticsMetadataProvider.added(item);

        Metadata oldMetadata = Objects.requireNonNull(getMetadata(item));
        assertEquals("Equipment_Door", oldMetadata.getValue());
        assertEquals(GROUP_ITEM_NAME, oldMetadata.getConfiguration().get("hasLocation"));

        when(itemRegistry.get(GROUP_ITEM_NAME)).thenReturn(null);

        semanticsMetadataProvider.removed(groupItem);

        Metadata metadata = Objects.requireNonNull(getMetadata(item));
        assertEquals("Equipment_Door", metadata.getValue());
        assertNull(metadata.getConfiguration().get("hasLocation"));
    }

    @Test
    public void testRecursiveGroupMembershipDoesNotResultInStackOverflowError() {
        GroupItem groupItem1 = new GroupItem("group1");
        GroupItem groupItem2 = new GroupItem("group2");

        groupItem1.addMember(groupItem2);
        groupItem2.addMember(groupItem1);

        assertDoesNotThrow(() -> semanticsMetadataProvider.added(groupItem1));

        assertLogMessage(SemanticsMetadataProvider.class, LogLevel.ERROR,
                "Recursive group membership found: group1 is a member of group2, but it is also one of its ancestors.");
    }

    @Test
    public void testIndirectRecursiveMembershipDoesNotThrowStackOverflowError() {
        GroupItem groupItem1 = new GroupItem("group1");
        GroupItem groupItem2 = new GroupItem("group2");
        GroupItem groupItem3 = new GroupItem("group3");

        groupItem1.addMember(groupItem2);
        groupItem2.addMember(groupItem3);
        groupItem3.addMember(groupItem1);

        assertDoesNotThrow(() -> semanticsMetadataProvider.added(groupItem1));

        assertLogMessage(SemanticsMetadataProvider.class, LogLevel.ERROR,
                "Recursive group membership found: group1 is a member of group3, but it is also one of its ancestors.");
    }

    @Test
    public void testDuplicateMembershipOfPlainItemsDoesNotTriggerWarning() {
        GroupItem groupItem1 = new GroupItem("group1");
        GroupItem groupItem2 = new GroupItem("group2");
        NumberItem numberItem = new NumberItem("number");

        groupItem1.addMember(groupItem2);
        groupItem1.addMember(numberItem);
        groupItem2.addMember(numberItem);

        semanticsMetadataProvider.added(groupItem1);

        assertNoLogMessage(SemanticsMetadataProvider.class);
    }

    @Test
    public void testDuplicateMembershipOfGroupItemsDoesNotTriggerWarning() {
        GroupItem groupItem1 = new GroupItem("group1");
        GroupItem groupItem2 = new GroupItem("group2");
        GroupItem groupItem3 = new GroupItem("group3");

        groupItem1.addMember(groupItem2);
        groupItem1.addMember(groupItem3);
        groupItem2.addMember(groupItem3);

        semanticsMetadataProvider.added(groupItem1);

        assertNoLogMessage(SemanticsMetadataProvider.class);
    }

    private @Nullable Metadata getMetadata(Item item) {
        return semanticsMetadataProvider.getAll().stream() //
                .filter(metadata -> metadata.getUID().getItemName().equals(item.getName())) //
                .findFirst() //
                .orElse(null);
    }
}
