package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.NeedsPlayerNotice;
import dev.hycolony.core.colony.ui.RequestsView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.colony.ui.UiPort;
import dev.hycolony.core.colony.ui.WandView;
import dev.hycolony.core.construction.wand.WandActions;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.BuildingPage;
import dev.hycolony.plugin.ui.FoundColonyPage;
import dev.hycolony.plugin.ui.RequestsPage;
import dev.hycolony.plugin.ui.citizen.CitizenInventoryWindows;
import dev.hycolony.plugin.ui.citizen.CitizenPage;
import dev.hycolony.plugin.ui.townhall.TownHallPage;
import dev.hycolony.plugin.ui.wand.WandPage;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.joml.Vector3i;

/** Renders core view models with Hytale custom pages. World thread only. */
public final class HytaleUiPort implements UiPort {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final Supplier<ColonyManager> manager;
    private final Supplier<WandActions> wand;
    private final HytaleBlocks blocks;
    private final IdMap ids;
    private final String townHallBlockId;
    private final String townHallItemId;
    private final CitizenInventoryWindows citizenInventories;
    /** Players whose page Hytale is closing right now: close() must not close it a second time. */
    private final Set<UUID> closing = new HashSet<>();

    public HytaleUiPort(Supplier<ColonyManager> manager, Supplier<WandActions> wand, HytaleBlocks blocks, IdMap ids) {
        this.manager = manager;
        this.citizenInventories = new CitizenInventoryWindows(manager);
        this.wand = wand;
        this.blocks = blocks;
        this.ids = ids;
        this.townHallBlockId = ids.blockId("hut.townhall");
        this.townHallItemId = ids.itemId("hut.townhall");
    }

    private void removeTownHall(BlockPos pos) {
        blocks.removeWithDrop(pos, townHallBlockId, townHallItemId);
    }

    @Override
    public void showFoundColony(UUID player, FoundColonyView view) {
        open(
                player,
                pr -> new FoundColonyPage(pr, view, new FoundColonyPage.Handler() {
                    @Override
                    public boolean confirm(String name) {
                        ColonyManager m = manager.get();
                        Optional<BlockPos> pos = m.foundation().pendingPositionOf(player);
                        boolean created = m.foundation().confirm(player, name).isPresent();
                        if (!created && m.foundation().pendingPositionOf(player).isEmpty()) {
                            pos.ifPresent(HytaleUiPort.this::removeTownHall); // spot became invalid: foundation dropped
                        }
                        return created;
                    }

                    @Override
                    public void cancel(boolean windowClosing) {
                        if (windowClosing) {
                            closing.add(player);
                        }
                        try {
                            manager.get().foundation().cancel(player).ifPresent(HytaleUiPort.this::removeTownHall);
                        } finally {
                            closing.remove(player);
                        }
                    }
                }));
    }

    @Override
    public void showTownHall(UUID player, TownHallView view) {
        open(player, (pr, previous) -> new TownHallPage(pr, view, manager.get()).keepTabOf(previous));
    }

    @Override
    public void showBuilding(UUID player, BuildingView view) {
        open(
                player,
                (pr, previous) ->
                        new BuildingPage(pr, view, manager.get(), () -> pickUp(player, view)).keepTabOf(previous));
    }

    @Override
    public void showRequests(UUID player, RequestsView view) {
        open(player, pr -> new RequestsPage(pr, view, manager.get()));
    }

    @Override
    public void showCitizen(UUID player, CitizenView view) {
        open(player, (pr, previous) -> new CitizenPage(pr, view, manager.get(), ids).keepTabOf(previous));
    }

    @Override
    public void showWand(UUID player, WandView view) {
        open(player, pr -> new WandPage(pr, view, manager.get(), wand.get(), ids));
    }

    @Override
    public void openCitizenInventory(UUID player, int colonyId, int citizenId) {
        citizenInventories.open(player, colonyId, citizenId);
    }

    @Override
    public void notifyNeedsPlayer(UUID player, NeedsPlayerNotice notice) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr != null) {
            pr.sendMessage(RequestsPage.needsPlayer(notice));
        }
    }

    /** "Pick up": the hut item goes to the player's inventory; once the core agrees, the block goes without a drop. */
    private void pickUp(UUID player, BuildingView view) {
        ColonyManager m = manager.get();
        BuildingType type = m.context().buildingTypes().byId(view.typeId()).orElse(null);
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        if (type == null || ref == null || !ref.isValid()) {
            return;
        }
        String itemId = ids.itemId(type.hutBlockKey());
        if (m.huts().pickUp(player, view.pos(), () -> give(ref, itemId))) {
            removeWithoutDrop(ref.getStore().getExternalData().getWorld(), view.pos(), ids.blockId(type.hutBlockKey()));
        }
    }

    /**
     * Removes the hut block if it is still {@code blockId}, with no item drop (the player already got it). Its
     * container's contents still spill on the ground (ItemContainerSystems drops them on any removal).
     */
    private static void removeWithoutDrop(World world, BlockPos pos, String blockId) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> section = cs.getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        BlockSection blocks =
                section == null ? null : cs.getStore().getComponent(section, BlockSection.getComponentType());
        if (blocks == null) {
            return;
        }
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(pos.x(), pos.y(), pos.z()));
        if (type == null || !(blockId.equals(type.getId()) || blockId.equals(type.getDefaultStateKey()))) {
            // The core already dropped the building and the player has the item: the block is left alone.
            LOG.at(Level.WARNING).log(
                    "HyColony pick-up: expected %s at %s, found %s; block left in place",
                    blockId, pos, type == null ? "nothing" : type.getId());
            return;
        }
        BlockHarvestUtils.naturallyRemoveBlock(
                new Vector3i(pos.x(), pos.y(), pos.z()),
                type,
                blocks.getFiller(pos.x(), pos.y(), pos.z()),
                0,
                null,
                null,
                SetBlockSettings.NO_DROP_ITEMS,
                section,
                world.getEntityStore().getStore(),
                cs.getStore());
    }

    /** One {@code itemId} into hotbar then storage; true only if it fit. */
    private static boolean give(Ref<EntityStore> ref, String itemId) {
        ItemContainer inv = InventoryComponent.getCombined(ref.getStore(), ref, InventoryComponent.HOTBAR_FIRST);
        return ItemStack.isEmpty(inv.addItemStack(new ItemStack(itemId, 1)).getRemainder());
    }

    @Override
    public void close(UUID player) {
        if (closing.contains(player)) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        if (ref == null || !ref.isValid()) {
            return; // disconnected or not in a world
        }
        Store<EntityStore> store = ref.getStore();
        store.getComponent(ref, Player.getComponentType()).getPageManager().setPage(ref, store, Page.None);
    }

    private void open(UUID player, Function<PlayerRef, CustomUIPage> page) {
        open(player, (pr, previous) -> page.apply(pr));
    }

    /** {@code page} also gets the page the player has open now (null if none), to keep its local state. */
    private void open(UUID player, BiFunction<PlayerRef, CustomUIPage, CustomUIPage> page) {
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PageManager pages = store.getComponent(ref, Player.getComponentType()).getPageManager();
        pages.openCustomPage(ref, store, page.apply(pr, pages.getCustomPage()));
    }
}
