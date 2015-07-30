package com.plotsquared.bukkit.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.Plugin;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.flag.Flag;
import com.intellectualcrafters.plot.flag.FlagManager;
import com.intellectualcrafters.plot.generator.ClassicPlotWorld;
import com.intellectualcrafters.plot.generator.HybridUtils;
import com.intellectualcrafters.plot.object.ChunkLoc;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotAnalysis;
import com.intellectualcrafters.plot.object.PlotBlock;
import com.intellectualcrafters.plot.object.PlotWorld;
import com.intellectualcrafters.plot.object.RunnableVal;
import com.intellectualcrafters.plot.util.ChunkManager;
import com.intellectualcrafters.plot.util.MainUtil;
import com.intellectualcrafters.plot.util.MathMan;
import com.intellectualcrafters.plot.util.TaskManager;
import com.plotsquared.bukkit.BukkitMain;
import com.plotsquared.bukkit.util.bukkit.BukkitUtil;

public class BukkitHybridUtils extends HybridUtils {

    public static List<ChunkLoc> regions;
    public static List<ChunkLoc> chunks = new ArrayList<>();
    public static String world;
    private static boolean UPDATE = false;
    public int task;
    private long last;
    
    @Override
    public void analyzePlot(final Plot plot, final RunnableVal<PlotAnalysis> whenDone) {
        // TODO Auto-generated method stub
        // int diff, int variety, int verticies, int rotation, int height_sd

        /*
         * diff: compare to base by looping through all blocks
         * variety: add to hashset for each plotblock
         * height_sd: loop over all blocks and get top block
         *
         * verticies: store air map and compare with neighbours
         * for each block check the adjacent
         *  - Store all blocks then go through in second loop
         *  - recheck each block
         *
         */
        final World world = Bukkit.getWorld(plot.world);
        final ChunkGenerator gen = world.getGenerator();
        if (gen == null) {
            return;
        }
        final BiomeGrid base = new BiomeGrid() { @Override public void setBiome(int a, int b, Biome c) {} @Override public Biome getBiome(int a, int b) {return null;}};
        Location bot = MainUtil.getPlotBottomLoc(plot.world, plot.id).add(1, 0, 1);
        Location top = MainUtil.getPlotTopLoc(plot.world, plot.id);
        final int bx = bot.getX();
        final int bz = bot.getZ();
        final int tx = top.getX();
        final int tz = top.getZ();
        final int cbx = bx >> 4;
        final int cbz = bz >> 4;
        final int ctx = tx >> 4;
        final int ctz = tz >> 4;
        final Random r = new Random();
        MainUtil.initCache();
        final int width = tx - bx + 1;
        final int length = tz - bz + 1;

        System.gc();
        System.gc();
        final short[][][] oldblocks = new short[256][width][length];
        final short[][][] newblocks = new short[256][width][length];

        final List<ChunkLoc> chunks = new ArrayList<>();
        final List<ChunkLoc> processed_chunks = new ArrayList<>();

        for (int X = cbx; X <= ctx; X++) {
            for (int Z = cbz; Z <= ctz; Z++) {
//                Chunk chunk = world.getChunkAt(X, Z);
                chunks.add(new ChunkLoc(X, Z));
            }
        }
        final Runnable run = new Runnable() {
            @Override
            public void run() {
                for (ChunkLoc chunk : processed_chunks) {
                    short[][] result = gen.generateExtBlockSections(world, r, chunk.x, chunk.z, base);
                    int X = chunk.x;
                    int Z = chunk.z;
                    int xb = ((X) << 4) - bx;
                    int zb = ((Z) << 4) - bz;
                    for (int i = 0; i < result.length; i++) {
                        if (result[i] == null) {
                            for (int j = 0; j < 4096; j++) {
                                int x = MainUtil.x_loc[i][j] + xb;
                                if (x < 0 || x >= width) continue;
                                int z = MainUtil.z_loc[i][j] + zb;
                                if (z < 0 || z >= length) continue;
                                int y = MainUtil.y_loc[i][j];
                                oldblocks[y][x][z] = 0;
                            }
                            continue;
                        }
                        for (int j = 0; j < result[i].length; j++) {
                            int x = MainUtil.x_loc[i][j] + xb;
                            if (x < 0 || x >= width) continue;
                            int z = MainUtil.z_loc[i][j] + zb;
                            if (z < 0 || z >= length) continue;
                            int y = MainUtil.y_loc[i][j];
                            oldblocks[y][x][z] = result[i][j];
                        }
                    }
                }
                int size = width * length;
                int[] changes = new int[size];
                int[] faces = new int[size];
                int[] data = new int[size];
                int[] air = new int[size];
                int[] variety = new int[size];

                int i = 0;
                for (int x = 0; x < width;x++) {
                    for (int z = 0; z < length;z++) {
                        HashSet<Short> types = new HashSet<>();
                        for (int y = 0; y < 256; y++) {
                            short old = oldblocks[y][x][z];
                            short now = newblocks[y][x][z];
                            if (old != now) {
                                changes[i]++;
                            }
                            if (now == 0) {
                                air[i]++;
                            }
                            else {
                                // check verticies
                                // modifications_adjacent
                                if (x > 0 && z > 0 && y > 0 && x < width - 1 && z < length - 1 && y < 255) {
                                    if (newblocks[y - 1][x][z] == 0) faces[i]++;
                                    if (newblocks[y][x - 1][z] == 0) faces[i]++;
                                    if (newblocks[y][x][z - 1] == 0) faces[i]++;
                                    if (newblocks[y + 1][x][z] == 0) faces[i]++;
                                    if (newblocks[y][x + 1][z] == 0) faces[i]++;
                                    if (newblocks[y][x][z + 1] == 0) faces[i]++;
                                }

                                Material material = Material.getMaterial(now);
                                Class<? extends MaterialData> md = material.getData();
                                if (md.equals(Directional.class)) {
                                    data[i] += 8;
                                }
                                else if (!md.equals(MaterialData.class)) {
                                    data[i]++;
                                }
                                types.add(now);
                            }
                        }
                        variety[i] = types.size();
                        i++;
                    }
                }
                // analyze plot
                // put in analysis obj

                // run whenDone
                PlotAnalysis analysis = new PlotAnalysis();
                analysis.changes = (int) (MathMan.getMean(changes) * 100);
                analysis.faces = (int) (MathMan.getMean(faces) * 100);
                analysis.data = (int) (MathMan.getMean(data) * 100);
                analysis.air = (int) (MathMan.getMean(air) * 100);
                analysis.variety = (int) (MathMan.getMean(variety) * 100);
                
                analysis.changes_sd = (int) (MathMan.getSD(changes, analysis.changes));
                analysis.faces_sd = (int) (MathMan.getSD(faces, analysis.faces));
                analysis.data_sd = (int) (MathMan.getSD(data, analysis.data));
                analysis.air_sd = (int) (MathMan.getSD(air, analysis.air));
                analysis.variety_sd = (int) (MathMan.getSD(variety, analysis.variety));
                
                List<Integer> result = new ArrayList<>();
                result.add(analysis.changes);
                result.add(analysis.faces);
                result.add(analysis.data);
                result.add(analysis.air);
                result.add(analysis.variety);
                
                result.add(analysis.changes_sd);
                result.add(analysis.faces_sd);
                result.add(analysis.data_sd);
                result.add(analysis.air_sd);
                result.add(analysis.variety_sd);
                Flag flag = new Flag(FlagManager.getFlag("analysis"), result);
                FlagManager.addPlotFlag(plot, flag);
                System.gc();
                System.gc();
                whenDone.value = analysis;
                whenDone.run();
            }
        };
        System.gc();
        MainUtil.initCache();
        TaskManager.index.incrementAndGet();
        final Integer currentIndex = TaskManager.index.get();
        final Integer task = TaskManager.runTaskRepeat(new Runnable() {
            @Override
            public void run() {
                int index = chunks.size() - 1;
                if (index == -1) {
                    PS.get().TASK.cancelTask(TaskManager.tasks.get(currentIndex));
                    TaskManager.runTaskAsync(run);
                    return;
                }
                ChunkLoc chunk = chunks.remove(0);
                world.loadChunk(chunk.x, chunk.z);
                processed_chunks.add(chunk);
                int X = chunk.x;
                int Z = chunk.z;
                int minX;
                int minZ;
                int maxX;
                int maxZ;
                if (X == cbx) minX = bx & 0x0f;
                else minX = 0;
                if (Z == cbz) minZ = bz & 0x0f;
                else minZ = 0;
                if (X == ctx) maxX = tx & 0x0f;
                else maxX = 16;
                if (Z == ctz) maxZ = tz & 0x0f;
                else maxZ = 16;
                
                int cbx = X << 4;
                int cbz = Z << 4;
                
                int xb = (cbx) - bx;
                int zb = (cbz) - bz;
                for (int x = minX; x <= maxX; x++) {
                    int xx = cbx + cbz;
                    for (int z = minZ; z <= maxZ; z++) {
                        int zz = cbz + z;
                        for (int y = 0; y < 256; y++) {
                            Block block = world.getBlockAt(xx, y, zz);
                            int xr = xb + x;
                            int zr = zb + z;
                            newblocks[y][xr][zr] = (short) block.getTypeId();
                        }
                    }
                }
                world.unloadChunkRequest(chunk.x, chunk.z, true);
            }
        }, 1);
        TaskManager.tasks.put(currentIndex, task);
    }

    public void checkModified(final Plot plot, final RunnableVal<Integer> whenDone) {
        TaskManager.index.incrementAndGet();
        final Location bot = MainUtil.getPlotBottomLoc(plot.world, plot.id).add(1, 0, 1);
        final Location top = MainUtil.getPlotTopLoc(plot.world, plot.id);
        int bx = bot.getX() >> 4;
        int bz = bot.getZ() >> 4;
        int tx = top.getX() >> 4;
        int tz = top.getZ() >> 4;
        World world = BukkitUtil.getWorld(plot.world);
        final HashSet<Chunk> chunks = new HashSet<>();
        for (int X = bx; X <= tx; X++) {
            for (int Z = bz; Z <= tz; Z++) {
                chunks.add(world.getChunkAt(X,Z));
            }
        }
        PlotWorld plotworld = PS.get().getPlotWorld(plot.world);
        if (!(plotworld instanceof ClassicPlotWorld)) {
            whenDone.value = -1;
        	TaskManager.runTaskLater(whenDone, 1);
        	return;
        }

        final ClassicPlotWorld cpw = (ClassicPlotWorld) plotworld;

        final AtomicInteger count = new AtomicInteger(0);

        final Integer currentIndex = TaskManager.index.get();
        final Integer task = TaskManager.runTaskRepeat(new Runnable() {
            @Override
            public void run() {
                if (chunks.size() == 0) {
                    whenDone.value = count.intValue();
                    TaskManager.runTaskLater(whenDone, 1);
                    Bukkit.getScheduler().cancelTask(TaskManager.tasks.get(currentIndex));
                    TaskManager.tasks.remove(currentIndex);
                    return;
                }
                Iterator<Chunk> iter = chunks.iterator();
                final Chunk chunk = iter.next();
                iter.remove();
                int bx = Math.max(chunk.getX() << 4, bot.getX());
                int bz = Math.max(chunk.getZ() << 4, bot.getZ());
                int ex = Math.min((chunk.getX() << 4) + 15, top.getX());
                int ez = Math.min((chunk.getZ() << 4) + 15, top.getZ());
                // count changes
                count.addAndGet(checkModified(plot.world, bx, ex, 1, cpw.PLOT_HEIGHT - 1, bz, ez, cpw.MAIN_BLOCK));
                count.addAndGet(checkModified(plot.world, bx, ex, cpw.PLOT_HEIGHT, cpw.PLOT_HEIGHT, bz, ez, cpw.TOP_BLOCK));
                count.addAndGet(checkModified(plot.world, bx, ex, cpw.PLOT_HEIGHT + 1, 255, bz, ez, new PlotBlock[] { new PlotBlock((short) 0, (byte) 0) }));
            }
        }, 1);
        TaskManager.tasks.put(currentIndex, task);

	}

    @Override
    public int checkModified(final String worldname, final int x1, final int x2, final int y1, final int y2, final int z1, final int z2, final PlotBlock[] blocks) {
        final World world = BukkitUtil.getWorld(worldname);
        int count = 0;
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    final Block block = world.getBlockAt(x, y, z);
                    final int id = block.getTypeId();
                    boolean same = false;
                    for (final PlotBlock p : blocks) {
                        if (id == p.id) {
                            same = true;
                            break;
                        }
                    }
                    if (!same) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    public int get_ey(final String worldname, final int sx, final int ex, final int sz, final int ez, final int sy) {
        final World world = BukkitUtil.getWorld(worldname);
        final int maxY = world.getMaxHeight();
        int ey = sy;
        for (int x = sx; x <= ex; x++) {
            for (int z = sz; z <= ez; z++) {
                for (int y = sy; y < maxY; y++) {
                    if (y > ey) {
                        final Block block = world.getBlockAt(x, y, z);
                        if (block.getTypeId() != 0) {
                            ey = y;
                        }
                    }
                }
            }
        }
        return ey;
    }
    
    public void regenerateChunkChunk(final String worldname, final ChunkLoc loc) {
        final World world = BukkitUtil.getWorld(worldname);
        final int sx = loc.x << 5;
        final int sz = loc.z << 5;
        for (int x = sx; x < (sx + 32); x++) {
            for (int z = sz; z < (sz + 32); z++) {
                final Chunk chunk = world.getChunkAt(x, z);
                chunk.load(false);
            }
        }
        final ArrayList<Chunk> chunks2 = new ArrayList<>();
        for (int x = sx; x < (sx + 32); x++) {
            for (int z = sz; z < (sz + 32); z++) {
                final Chunk chunk = world.getChunkAt(x, z);
                chunks2.add(chunk);
                regenerateRoad(worldname, new ChunkLoc(x, z), 0);
                MainUtil.update(world.getName(), new ChunkLoc(chunk.getX(), chunk.getZ()));
            }
        }
    }

    public final ArrayList<ChunkLoc> getChunks(ChunkLoc region) {
    	ArrayList<ChunkLoc> chunks = new ArrayList<ChunkLoc>();
    	final int sx = region.x << 5;
        final int sz = region.z << 5;
        for (int x = sx; x < (sx + 32); x++) {
            for (int z = sz; z < (sz + 32); z++) {
            	chunks.add(new ChunkLoc(x, z));
            }
        }
        return chunks;
    }

    @Override
    public boolean scheduleRoadUpdate(final String world, int extend) {
        if (BukkitHybridUtils.UPDATE) {
            return false;
        }
        BukkitHybridUtils.UPDATE = true;
        final List<ChunkLoc> regions = ChunkManager.manager.getChunkChunks(world);
        return scheduleRoadUpdate(world, regions, extend);
    }
    
    public boolean scheduleRoadUpdate(final String world, final List<ChunkLoc> rgs, final int extend) {
        BukkitHybridUtils.regions = rgs;
        BukkitHybridUtils.world = world;
        chunks = new ArrayList<ChunkLoc>();
        final Plugin plugin = BukkitMain.THIS;
        final AtomicInteger count = new AtomicInteger(0);
        this.task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                count.incrementAndGet();
                if (count.intValue() % 20 == 0) {
                    PS.debug("PROGRESS: " + ((100 * (2048 - chunks.size())) / 2048) + "%");
                }
                if (regions.size() == 0 && chunks.size() == 0) {
                    BukkitHybridUtils.UPDATE = false;
                    PS.debug(C.PREFIX.s() + "Finished road conversion");
                    Bukkit.getScheduler().cancelTask(BukkitHybridUtils.this.task);
                    return;
                } else {
                    try {
                    	if (chunks.size() < 1024) {
                    	    if (regions.size() > 0) {
                        		final ChunkLoc loc = regions.get(0);
                                PS.debug("&3Updating .mcr: " + loc.x + ", " + loc.z + " (aprrox 1024 chunks)");
                                PS.debug(" - Remaining: " + regions.size());
                        		chunks.addAll(getChunks(loc));
                        		regions.remove(0);
                        		System.gc();
                    	    }
                    	}
                    	if (chunks.size() > 0) {
                    		long diff = System.currentTimeMillis() + 25;
                    		if (System.currentTimeMillis() - last > 1200 && last != 0) {
                    		    last = 0;
                    		    PS.debug(C.PREFIX.s() + "Detected low TPS. Rescheduling in 30s");
                    		    while (chunks.size() > 0) {
                                    ChunkLoc chunk = chunks.get(0);
                                    chunks.remove(0);
                                    regenerateRoad(world, chunk, extend);
                                    ChunkManager.manager.unloadChunk(world, chunk, true, true);
                                }
                                Bukkit.getScheduler().cancelTask(BukkitHybridUtils.this.task);
                                TaskManager.runTaskLater(new Runnable() {
                                    @Override
                                    public void run() {
                                       scheduleRoadUpdate(world, regions, extend); 
                                    }
                                }, 600);
                                return;
                    		}
                    		if (System.currentTimeMillis() - last < 1000) {
                        		while (System.currentTimeMillis() < diff && chunks.size() > 0) {
                        			ChunkLoc chunk = chunks.get(0);
                        			chunks.remove(0);
                        			regenerateRoad(world, chunk, extend);
                        			ChunkManager.manager.unloadChunk(world, chunk, true, true);
                        		}
                    		}
                    		last = System.currentTimeMillis();
                    	}
                    } catch (final Exception e) {
                        e.printStackTrace();
                        final ChunkLoc loc = regions.get(0);
                        PS.debug("&c[ERROR]&7 Could not update '" + world + "/region/r." + loc.x + "." + loc.z + ".mca' (Corrupt chunk?)");
                        final int sx = loc.x << 5;
                        final int sz = loc.z << 5;
                        for (int x = sx; x < (sx + 32); x++) {
                            for (int z = sz; z < (sz + 32); z++) {
                                ChunkManager.manager.unloadChunk(world, new ChunkLoc(x, z), true, true);
                            }
                        }
                        PS.debug("&d - Potentially skipping 1024 chunks");
                        PS.debug("&d - TODO: recommend chunkster if corrupt");
                    }
                }
            }
        }, 20, 20);
        return true;
    }
}
