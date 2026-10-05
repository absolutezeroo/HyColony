package dev.hyangler.plugin.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hyangler.core.FishingService;
import dev.hyangler.core.catalog.RawFile;
import dev.hyangler.core.catalog.RawFile.Kind;
import dev.hyangler.core.catalog.Rejection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Hands the data files to the core once, at HyAngler's start: after the assets have loaded and after every mod's setup,
 * where condition types register (spec § 10). A reload of the assets needs a restart, as HyColony's foods do. A file
 * naming an unknown item is left out here (the core knows no items).
 */
public final class AnglerData {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /**
     * The rarity states of Hytale's fish items (fishing-hytale.md § 5.5): an item with any of them has rarities. Not
     * every fish has all four (Fish_Tang_Blue_Item starts at Rare, Fish_Jellyfish_Blue_Item has only Legendary).
     */
    private static final List<String> RARITY_STATES = List.of("Uncommon", "Rare", "Epic", "Legendary");

    private final FishingService service;
    private boolean loaded; // guarded by this
    private volatile List<String> unknownItems = List.of();
    private volatile int filesRead;

    public AnglerData(FishingService service) {
        this.service = service;
    }

    /**
     * Loads the files the first time; later calls do nothing, and a caller during the load waits for it. One try
     * only: a failure is logged and the catalog stays empty until a restart (spec § 10). Never throws.
     */
    public synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            List<RawFile> files = new ArrayList<>();
            List<String> unknown = new ArrayList<>();
            FishAsset.all().forEach((id, f) -> add(files, unknown, new RawFile(Kind.FISH, id, f.json())));
            CatchAsset.all().forEach((id, f) -> add(files, unknown, new RawFile(Kind.CATCH, id, f.json())));
            RodAsset.all().forEach((id, f) -> add(files, unknown, new RawFile(Kind.ROD, id, f.json())));
            service.load(files, rarityItems(files));
            unknownItems = List.copyOf(unknown);
            filesRead = files.size() + unknown.size();
            report(unknown);
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyAngler: the data files could not be read");
        }
    }

    /** The files whose item does not exist, as "KIND id"; empty before the first load. */
    public List<String> unknownItems() {
        return unknownItems;
    }

    /** How many data files were found, read or left out; 0 before the first load. */
    public int filesRead() {
        return filesRead;
    }

    /** Logs the files left out (unknown item, rejected by the core) in WARNING, then the counts read, in INFO. */
    private void report(List<String> unknown) {
        unknown.forEach(id -> LOG.at(Level.WARNING).log("HyAngler: %s names no item, left out", id));
        for (Rejection r : service.catalog().rejections()) {
            LOG.at(Level.WARNING).log("HyAngler: %s %s left out: %s", r.kind(), r.id(), r.reason());
        }
        LOG.at(Level.INFO).log(
                "HyAngler: %d fish, %d junk, %d treasure, %d rods",
                service.catalog().fish().size(),
                service.catalog().junk().size(),
                service.catalog().treasure().size(),
                service.catalog().rods().size());
    }

    /** Adds file to files when its item exists, else its "KIND id" to unknown. */
    private static void add(List<RawFile> files, List<String> unknown, RawFile file) {
        if (Item.getAssetMap().getAsset(file.id()) == null) {
            unknown.add(file.kind() + " " + file.id());
        } else {
            files.add(file);
        }
    }

    /** The fish whose item has rarity states (Item.getItemIdForState, fishing-hytale.md § 5.5). */
    private static Set<String> rarityItems(List<RawFile> files) {
        Set<String> out = new HashSet<>();
        for (RawFile f : files) {
            Item item = Item.getAssetMap().getAsset(f.id());
            if (f.kind() == Kind.FISH && item != null && hasRarity(item)) {
                out.add(f.id());
            }
        }
        return out;
    }

    /** Whether item has any of the rarity states. */
    private static boolean hasRarity(Item item) {
        return RARITY_STATES.stream().anyMatch(state -> item.getItemIdForState(state) != null);
    }
}
