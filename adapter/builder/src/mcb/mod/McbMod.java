package mcb.mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's entry point. The commands come in through {@code mcb.mod.mixin.CommandsMixin}. */
public final class McbMod implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("mcb");

    @Override public void onInitialize() {
        mcb.mc.BlockFacts.install();   // (the planner's block tables come from this game, filled at the first build)
        LOG.info("[mcb] MCB Builder loaded: /mcb build <schematic> [x y z]");
    }
}
