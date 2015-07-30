package com.plotsquared.sponge;

import org.spongepowered.api.text.title.Title;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.util.AbstractTitle;

public class SpongeTitleManager extends AbstractTitle {
    
    @Override
    public void sendTitle(PlotPlayer player, String head, String sub, int in, int delay, int out) {
        ((SpongePlayer) player).player.sendTitle(new Title(SpongeMain.THIS.getText(head), SpongeMain.THIS.getText(sub), in * 20, delay * 20, out * 20, false, false));
    }
}
