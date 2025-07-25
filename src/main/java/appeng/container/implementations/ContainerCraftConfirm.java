/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;


import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.core.AELog;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.helpers.PlayerSource;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;
import appeng.util.Platform;
import com.google.common.collect.ImmutableSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;


public class ContainerCraftConfirm extends AEBaseContainer {

    private final ArrayList<CraftingCPURecord> cpus = new ArrayList<>();
    private Future<ICraftingJob> job;
    private ICraftingJob result;
    @GuiSync(0)
    public long bytesUsed;
    @GuiSync(1)
    public long cpuBytesAvail;
    @GuiSync(2)
    public int cpuCoProcessors;
    @GuiSync(3)
    public boolean autoStart = false;
    @GuiSync(4)
    public boolean simulation = true;
    @GuiSync(5)
    public int selectedCpu = -1;
    @GuiSync(6)
    public boolean noCPU = true;
    @GuiSync(7)
    public String myName = "";
    private GuiCraftConfirm guiCraftConfirm;

    public ContainerCraftConfirm(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    public void cycleCpu(final boolean next) {
        if (next) {
            this.setSelectedCpu(this.getSelectedCpu() + 1);
        } else {
            this.setSelectedCpu(this.getSelectedCpu() - 1);
        }

        if (this.getSelectedCpu() < -1) {
            this.setSelectedCpu(this.cpus.size() - 1);
        } else if (this.getSelectedCpu() >= this.cpus.size()) {
            this.setSelectedCpu(-1);
        }

        if (this.getSelectedCpu() == -1) {
            this.setCpuAvailableBytes(0);
            this.setCpuCoProcessors(0);
            this.setName("");
        } else {
            this.setName(this.cpus.get(this.getSelectedCpu()).getName());
            this.setCpuAvailableBytes(this.cpus.get(this.getSelectedCpu()).getSize());
            this.setCpuCoProcessors(this.cpus.get(this.getSelectedCpu()).getProcessors());
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isClient()) {
            return;
        }

        final IActionHost h = ((IActionHost) this.getTarget());
        if (h == null) {
            this.setValidContainer(false);
            return;
        }
        IGridNode node = h.getActionableNode();
        if (node == null) {
            this.setValidContainer(false);
            return;
        }
        IGrid grid = node.getGrid();

        final ICraftingGrid cc = grid.getCache(ICraftingGrid.class);
        final ImmutableSet<ICraftingCPU> cpuSet = cc.getCpus();

        int matches = 0;
        boolean changed = false;
        for (final ICraftingCPU c : cpuSet) {
            boolean found = false;
            for (final CraftingCPURecord ccr : this.cpus) {
                if (ccr.getCpu() == c) {
                    found = true;
                    break;
                }
            }

            final boolean matched = this.cpuMatches(c);

            if (matched) {
                matches++;
            }

            if (found == !matched) {
                changed = true;
            }
        }

        if (changed || this.cpus.size() != matches) {
            this.cpus.clear();
            for (final ICraftingCPU c : cpuSet) {
                if (this.cpuMatches(c)) {
                    this.cpus.add(new CraftingCPURecord(c.getAvailableStorage(), c.getCoProcessors(), c));
                }
            }

            this.sendCPUs();
        }

        this.setNoCPU(this.cpus.isEmpty());

        super.detectAndSendChanges();

        if (this.getJob() != null && this.getJob().isDone()) {
            try {
                this.result = this.getJob().get();

                if (!this.result.isSimulation()) {
                    this.setSimulation(false);
                    if (this.isAutoStart()) {
                        this.startJob();
                        return;
                    }
                } else {
                    this.setSimulation(true);
                }

                try {
                    final PacketMEInventoryUpdate a = new PacketMEInventoryUpdate((byte) 0);
                    final PacketMEInventoryUpdate b = new PacketMEInventoryUpdate((byte) 1);
                    final PacketMEInventoryUpdate c = this.result.isSimulation() ? new PacketMEInventoryUpdate((byte) 2) : null;
                    final PacketMEInventoryUpdate d = new PacketMEInventoryUpdate((byte) 3);
                    final PacketMEInventoryUpdate e = new PacketMEInventoryUpdate((byte) 4);

                    final IItemList<IAEItemStack> plan = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();
                    this.result.populatePlan(plan);

                    this.setUsedBytes(this.result.getByteTotal());

                    Map<IAEItemStack, Long> craftCounts = new HashMap<>();
                    for (final IAEItemStack item : plan) {
                        try {
                            IAEItemStack keyItem = item.copy().reset();
                            long count = this.result.getTotalCraftsForPrimaryOutput(keyItem);
                            if (count > 0) {
                                craftCounts.put(keyItem, count);

                                IAEItemStack countStack = keyItem.copy();
                                countStack.setStackSize(count);
                                d.appendItem(countStack);
                            }
                        } catch (IllegalArgumentException ex) {
                            AELog.debug("Invalid item for craft count: " + item, ex);
                        }
                    }

                    final IStorageGrid sg = grid.getCache(IStorageGrid.class);
                    final IMEMonitor<IAEItemStack> items = sg.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

                    for (final IAEItemStack out : plan) {
                        final long requested = out.getStackSize();

                        IAEItemStack o = out.copy();
                        o.reset();
                        o.setStackSize(requested);

                        IAEItemStack totalAvailable = items.getStorageList().findPrecise(out);
                        long maxAvailable = totalAvailable != null ? totalAvailable.getStackSize() : 0;

                        IAEItemStack simulatedExtract = items.extractItems(o, Actionable.SIMULATE, this.getActionSource());
                        long available = simulatedExtract != null ? simulatedExtract.getStackSize() : 0;

                        if (available > 0) {
                            a.appendItem(simulatedExtract);
                        }

                        final IAEItemStack p = out.copy();
                        p.reset();
                        p.setStackSize(out.getCountRequestable());
                        if (p.getStackSize() > 0) {
                            b.appendItem(p);
                        }

                        IAEItemStack m = null;
                        if (c != null && this.result.isSimulation()) {
                            m = o.copy();
                            o = simulatedExtract != null ? simulatedExtract : o.copy().setStackSize(0);
                            m.setStackSize(m.getStackSize() - o.getStackSize());
                            if (m.getStackSize() > 0) {
                                c.appendItem(m);
                            }
                        }

                        if (maxAvailable <= 0) {
                            continue;
                        }
                        if (requested > maxAvailable) {
                            continue;
                        }

                        double ratio = 0.0;
                        if (requested > 0) {
                        ratio = (double) available / maxAvailable;
                            ratio = Math.round(ratio * 10000.0) / 10000.0;
                        }

                        if (ratio <= 1.0 && out.getCountRequestable() == 0) {
                            IAEItemStack ratioStack = out.copy();
                            ratioStack.setStackSize((long) (ratio * 10000));
                            e.appendItem(ratioStack);
                        }
                    }

                    for (final Object g : this.listeners) {
                        if (g instanceof EntityPlayer) {
                            NetworkHandler.instance().sendTo(a, (EntityPlayerMP) g);
                            NetworkHandler.instance().sendTo(b, (EntityPlayerMP) g);
                            if (c != null) NetworkHandler.instance().sendTo(c, (EntityPlayerMP) g);
                            NetworkHandler.instance().sendTo(d, (EntityPlayerMP) g);
                            NetworkHandler.instance().sendTo(e, (EntityPlayerMP) g);
                        }
                    }
                } catch (final IOException e) {
                    // :P
                }
            } catch (final Throwable e) {
                this.getPlayerInv().player.sendMessage(new TextComponentString("Error: " + e));
                AELog.debug(e);
                this.setValidContainer(false);
                this.result = null;
            }

            this.setJob(null);
        }
        this.verifyPermissions(SecurityPermissions.CRAFT, false);
    }

    private boolean cpuMatches(final ICraftingCPU c) {
        return c.getAvailableStorage() >= this.getUsedBytes() && !c.isBusy();
    }

    private void sendCPUs() {
        Collections.sort(this.cpus);

        if (this.getSelectedCpu() >= this.cpus.size()) {
            this.setSelectedCpu(-1);
            this.setCpuAvailableBytes(0);
            this.setCpuCoProcessors(0);
            this.setName("");
        } else if (this.getSelectedCpu() != -1) {
            this.setName(this.cpus.get(this.getSelectedCpu()).getName());
            this.setCpuAvailableBytes(this.cpus.get(this.getSelectedCpu()).getSize());
            this.setCpuCoProcessors(this.cpus.get(this.getSelectedCpu()).getProcessors());
        }
    }

    public void startJob() {
        GuiBridge originalGui = null;

        final IActionHost ah = this.getActionHost();
        if (ah instanceof WirelessTerminalGuiObject) {
            ItemStack myIcon = ((WirelessTerminalGuiObject) ah).getItemStack();
            originalGui = (GuiBridge) AEApi.instance().registries().wireless().getWirelessTerminalHandler(myIcon).getGuiHandler(myIcon);
        }

        if (ah instanceof PartTerminal) {
            originalGui = GuiBridge.GUI_ME;
        }

        if (ah instanceof PartCraftingTerminal) {
            originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (ah instanceof PartPatternTerminal) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (ah instanceof PartExpandedProcessingPatternTerminal) {
            originalGui = GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }

        final IActionHost h = ((IActionHost) this.getTarget());
        if (h == null) {
            return;
        }
        IGridNode node = h.getActionableNode();
        if (node == null) {
            return;
        }
        IGrid grid = node.getGrid();

        if (this.result != null && !this.isSimulation()) {
            final ICraftingGrid cc = grid.getCache(ICraftingGrid.class);
            final ICraftingLink g = cc.submitJob(this.result, null, this.getSelectedCpu() == -1 ? null : this.cpus.get(this.getSelectedCpu()).getCpu(), true, this.getActionSrc());
            this.setAutoStart(false);
            if (g == null) {
                this.setJob(cc.beginCraftingJob(this.getWorld(), grid, this.getActionSrc(), this.result.getOutput(), null));
            } else if (originalGui != null && this.getOpenContext() != null) {
                final TileEntity te = this.getOpenContext().getTile();
                if (te != null) {
                    Platform.openGUI(this.getInventoryPlayer().player, te, this.getOpenContext().getSide(), originalGui);
                } else {
                    if (ah instanceof IInventorySlotAware i) {
                        Platform.openGUI(this.getInventoryPlayer().player, i.getInventorySlot(), originalGui, i.isBaubleSlot());
                    }
                }
            }
        }
    }

    private IActionSource getActionSrc() {
        return new PlayerSource(this.getPlayerInv().player, (IActionHost) this.getTarget());
    }

    @Override
    public void removeListener(final IContainerListener c) {
        super.removeListener(c);
        if (this.getJob() != null) {
            this.getJob().cancel(true);
            this.setJob(null);
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer par1EntityPlayer) {
        super.onContainerClosed(par1EntityPlayer);
        if (this.getJob() != null) {
            this.getJob().cancel(true);
            this.setJob(null);
        }
    }

    public World getWorld() {
        return this.getPlayerInv().player.world;
    }

    public boolean isAutoStart() {
        return this.autoStart;
    }

    public void setAutoStart(final boolean autoStart) {
        this.autoStart = autoStart;
    }

    public long getUsedBytes() {
        return this.bytesUsed;
    }

    private void setUsedBytes(final long bytesUsed) {
        this.bytesUsed = bytesUsed;
    }

    public long getCpuAvailableBytes() {
        return this.cpuBytesAvail;
    }

    private void setCpuAvailableBytes(final long cpuBytesAvail) {
        this.cpuBytesAvail = cpuBytesAvail;
    }

    public int getCpuCoProcessors() {
        return this.cpuCoProcessors;
    }

    private void setCpuCoProcessors(final int cpuCoProcessors) {
        this.cpuCoProcessors = cpuCoProcessors;
    }

    public int getSelectedCpu() {
        return this.selectedCpu;
    }

    private void setSelectedCpu(final int selectedCpu) {
        this.selectedCpu = selectedCpu;
    }

    public String getName() {
        return this.myName;
    }

    private void setName(@Nonnull final String myName) {
        this.myName = myName;
    }

    public boolean hasNoCPU() {
        return this.noCPU;
    }

    private void setNoCPU(final boolean noCPU) {
        this.noCPU = noCPU;
    }

    public boolean isSimulation() {
        return this.simulation;
    }

    private void setSimulation(final boolean simulation) {
        this.simulation = simulation;
    }

    private Future<ICraftingJob> getJob() {
        return this.job;
    }

    public void setJob(final Future<ICraftingJob> job) {
        this.job = job;
    }

    public void postUpdate(final List<IAEItemStack> list, final byte ref) {
        this.guiCraftConfirm.postUpdate(list, ref);
    }

    public void setGui(GuiCraftConfirm guiCraftConfirm) {
        this.guiCraftConfirm = guiCraftConfirm;
    }
}
