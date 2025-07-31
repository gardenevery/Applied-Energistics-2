/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.networking;


import javax.annotation.Nonnull;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkEvent;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.spatial.ISpatialCache;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.util.IReadOnlyCollection;


/**
 * Gives you access to Grid based information.
 *
 * Don't Implement.
 */
public interface IGrid
{

	/**
	 * Get Access to various grid modules
	 *
	 * @param iface face
	 *
	 * @return the IGridCache you requested.
	 */
	@Nonnull
	<C extends IGridCache> C getCache( @Nonnull Class<? extends IGridCache> iface );

	/**
	 * Post an event into the network event bus.
	 *
	 * @param ev - event to post
	 *
	 * @return returns ev back to original poster
	 */
	@Nonnull
	MENetworkEvent postEvent( @Nonnull MENetworkEvent ev );

	/**
	 * Post an event into the network event bus, but direct it at a single node.
	 *
	 * @param ev event to post
	 *
	 * @return returns ev back to original poster
	 */
	@Nonnull
	MENetworkEvent postEventTo( @Nonnull IGridNode node, @Nonnull MENetworkEvent ev );

	/**
	 * get a list of the diversity of classes, you can use this to better detect which machines your interested in,
	 * rather then iterating the entire grid to test them.
	 *
	 * @return IReadOnlyCollection of all available host types (Of Type IGridHost).
	 */
	@Nonnull
	IReadOnlyCollection<Class<? extends IGridHost>> getMachinesClasses();

	/**
	 * Get machines on the network.
	 *
	 * @param gridHostClass class of the grid host
	 *
	 * @return IMachineSet of all nodes belonging to hosts of specified class.
	 */
	@Nonnull
	IMachineSet getMachines( @Nonnull Class<? extends IGridHost> gridHostClass );

	/**
	 * @return IReadOnlyCollection for all nodes on the network, node visitors are preferred.
	 */
	@Nonnull
	IReadOnlyCollection<IGridNode> getNodes();

	/**
	 * @return true if the last node has been removed from the grid.
	 */
	boolean isEmpty();

	/**
	 * @return the node considered the pivot point of the grid.
	 */
	@Nonnull
	IGridNode getPivot();

	/**
	 * Get machine nodes on the network.
	 *
	 * @param machineClass class of the machine associated with a grid node
	 * @return all nodes belonging to machines of specified class. keep in mind that machines can have multiple nodes.
	 */
	@Nonnull
	Iterable<IGridNode> getMachineNodes(@Nonnull Class<?> machineClass);

	/**
	 * Get this grids {@link ITickManager}.
	 *
	 * @see #getCache(Class)
	 */
	@Nonnull
	default ITickManager getTickManager() {
		return getCache(ITickManager.class);
	}

	/**
	 * Get this grids {@link IStorageGrid}.
	 *
	 * @see #getCache(Class)
	 */
	@Nonnull
	default IStorageGrid getStorageGrid() {
		return getCache(IStorageGrid.class);
	}

	/**
	 * Get this grids {@link IEnergyGrid}.
	 *
	 * @see #getCache(Class)
	 */
	@Nonnull
	default IEnergyGrid getEnergyGrid() {
		return getCache(IEnergyGrid.class);
	}

	/**
	 * Get this grids {@link ICraftingGrid}.
	 *
	 * @see #getCache(Class)
	 */
	@Nonnull
	default ICraftingGrid getCraftingGrid() {
		return getCache(ICraftingGrid.class);
	}

	/**
	 * Get this grids {@link ISecurityGrid}.
	 *
	 * @see #getCache(Class)
	 */
	@Nonnull
	default ISecurityGrid getSecurityGrid() {
		return getCache(ISecurityGrid.class);
	}

	/**
	 * Get this grids {@link IPathingGrid}.
	 *
	 * @see #getCache(Class)
	 */
	@Nonnull
	default IPathingGrid getPathingGrid() {
		return getCache(IPathingGrid.class);
	}

	/**
	 * Get this grids {@link ISpatialCache}.
	 *
	 * @see #getCache(Class)
	 */
	@Nonnull
	default ISpatialCache getSpatialCache() {
		return getCache(ISpatialCache.class);
	}

}