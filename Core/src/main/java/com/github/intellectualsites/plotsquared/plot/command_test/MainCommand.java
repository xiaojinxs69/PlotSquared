package com.github.intellectualsites.plotsquared.plot.command_test;

import com.github.intellectualsites.plotsquared.plot.PlotSquared;
import com.github.intellectualsites.plotsquared.plot.command_test.binding.PlotSquaredBindings;
import com.github.intellectualsites.plotsquared.plot.config.Captions;
import com.github.intellectualsites.plotsquared.plot.object.ConsolePlayer;
import com.github.intellectualsites.plotsquared.plot.object.Location;
import com.github.intellectualsites.plotsquared.plot.object.Plot;
import com.github.intellectualsites.plotsquared.plot.object.PlotArea;
import com.github.intellectualsites.plotsquared.plot.object.PlotPlayer;
import com.github.intellectualsites.plotsquared.plot.util.Permissions;
import com.github.intellectualsites.plotsquared.plot.util.TaskManager;
import com.google.common.base.Joiner;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandLocals;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.BiomeCommands;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.event.platform.CommandSuggestionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.extension.platform.PlatformManager;
import com.sk89q.worldedit.internal.command.ActorAuthorizer;
import com.sk89q.worldedit.internal.command.UserCommandCompleter;
import com.sk89q.worldedit.internal.command.WorldEditBinding;
import com.sk89q.worldedit.util.command.Dispatcher;
import com.sk89q.worldedit.util.command.fluent.CommandGraph;
import com.sk89q.worldedit.util.command.parametric.ParametricBuilder;
import com.sk89q.worldedit.util.eventbus.Subscribe;

import java.util.Arrays;

public class MainCommand {
    private static MainCommand instance;

    private final WorldEdit we;
    private final PlotSquared ps;

    private final ParametricBuilder builder;
    private final Dispatcher dispatcher;

    public MainCommand(PlotSquared ps, WorldEdit worldEdit, PlatformManager platformManager) {
        this.we = worldEdit;
        this.ps = ps;

        worldEdit.getEventBus().register(this);

        builder = new ParametricBuilder();
        builder.setAuthorizer(new ActorAuthorizer());
        builder.setDefaultCompleter(new UserCommandCompleter(platformManager));
        builder.addBinding(new WorldEditBinding(worldEdit));
        builder.addBinding(new PlotSquaredBindings(ps));

        dispatcher = new CommandGraph()
                .builder(builder)
                .commands().

                        registerMethods(new BiomeCommands(worldEdit))

                .graph()
                .getDispatcher();
    }

    public void register() {
        Platform platform = we.getPlatformManager().queryCapability(Capability.USER_COMMANDS);
        platform.registerCommands(dispatcher);
    }

    public static MainCommand getInstance() {
        if (instance == null) {
            WorldEdit we = WorldEdit.getInstance();
            instance = new MainCommand(PlotSquared.get(), we, we.getPlatformManager());
            instance.register();
        }
        return instance;
    }

    public String[] commandDetection(CommandLocals locals, String[] split) {
        return split;
    }

    @Subscribe
    public void handleCommand(CommandEvent event) {
        Actor actor = event.getActor();
        PlotPlayer player = PlotPlayer.wrap(actor.getName());
        // TODO check player confirmation

        CommandLocals locals = new CommandLocals();
        locals.put(Actor.class, actor);
        locals.put(PlotPlayer.class, player);
        locals.put(Location.class, player.getLocation());
        locals.put(Plot.class, player.getCurrentPlot());
        locals.put("arguments", event.getArguments());

        String[] split = commandDetection(locals, event.getArguments().split(" "));


        // Optional command scope - Change plot for console //
        Location loc;
        Plot plot;
        boolean tp;
        if (split.length > 1 && split[0].indexOf(';') != -1 && dispatcher.contains(split[1])) {
            PlotArea area = player.getApplicablePlotArea();
            Plot newPlot = Plot.fromString(area, split[0]);

            if (newPlot != null
                &&
                (player instanceof ConsolePlayer ||
                    newPlot.getArea().equals(area) ||
                    Permissions.hasPermission(player, Captions.PERMISSION_ADMIN))
                &&
                !newPlot.isDenied(player.getUUID())) {

                Location newLoc = newPlot.getCenter();
                if (player.canTeleport(newLoc)) {
                    // Save meta
                    loc = player.getMeta(PlotPlayer.META_LOCATION);
                    plot = player.getMeta(PlotPlayer.META_LAST_PLOT);
                    tp = true;
                    // Set loc
                    player.setMeta(PlotPlayer.META_LOCATION, newLoc);
                    player.setMeta(PlotPlayer.META_LAST_PLOT, newPlot);
                    locals.put(Plot.class, plot);
                    locals.put(Location.class, loc);
                } else {
                    Captions.BORDER.send(player);
                    return;
                }
                // Trim command
                split = Arrays.copyOfRange(split, 1, split.length);
            } else {
                // Invalid command
                return;
            }
        } else if (!dispatcher.contains(split[0])) {
            // Invalid command
            return;
        } else {
            tp = false;
            plot = null;
            loc = null;
        }
        // End command scope //

        final String[] args = split;
        TaskManager.IMP.taskAsync(new Runnable() {
            @Override public void run() {
                synchronized (player) {
                    try {
                        // This is a bit of a hack, since the call method can only throw CommandExceptions
                        // everything needs to be wrapped at least once. Which means to handle all WorldEdit
                        // exceptions without writing a hook into every dispatcher, we need to unwrap these
                        // exceptions and rethrow their converted form, if their is one.
                        try {

                            // TODO
                            // Confirmation
                            // Cost

                            Command command = null; // TODO get command
                            // Check command cost

                            Object result = dispatcher.call(Joiner.on(" ").join(args), locals, new String[0]);

                            if (result == Boolean.TRUE) {
                                // If cost && true, then charge
                            }
                            System.out.println("Result " + result);
                        } catch (CommandException e) {
                            actor.print(e.getMessage());
                        } catch (Throwable t) {
                            // TODO p2 error handling
                        }
                    } finally {
                        // cleanup
                        event.setCancelled(true);

                        if (tp && !(player instanceof ConsolePlayer)) {
                            if (loc == null) {
                                player.deleteMeta(PlotPlayer.META_LOCATION);
                            } else {
                                player.setMeta(PlotPlayer.META_LOCATION, loc);
                            }
                            if (plot == null) {
                                player.deleteMeta(PlotPlayer.META_LAST_PLOT);
                            } else {
                                player.setMeta(PlotPlayer.META_LAST_PLOT, plot);
                            }
                        }
                    }
                }
            }
        });
    }

    @Subscribe
    public void handleCommandSuggestion(CommandSuggestionEvent event) {
        try {
            CommandLocals locals = new CommandLocals();
            locals.put(Actor.class, event.getActor());
            locals.put("arguments", event.getArguments());
            event.setSuggestions(dispatcher.getSuggestions(event.getArguments(), locals));
        } catch (CommandException e) {
            event.getActor().printError(e.getMessage());
        }
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }
}
