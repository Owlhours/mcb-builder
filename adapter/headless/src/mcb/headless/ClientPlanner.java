package mcb.headless;

import mcb.bot.Builder;
import mcb.bot.Grid;
import mcb.bot.Rules;
import mcb.build.Site;
import mcb.env.Client;
import mcb.env.Oracle;

/** How {@link ClientRun} makes a builder with the client mod's rules (one place, so that the client check can put an
 *  older planner behind the same runner; see tools/client-check). */
final class ClientPlanner {
    private ClientPlanner() { }
    static Builder builder(Client c, Grid g, Site site, Oracle oracle, int maxFall16) {
        return new Builder(c, g, site, oracle, Rules.client(maxFall16));
    }
}
