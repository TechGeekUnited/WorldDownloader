/*
 * This file is part of World Downloader: A mod to make backups of your
 * multiplayer worlds.
 * http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/2520465
 *
 * Copyright (c) 2014 nairol, cubic72
 * Copyright (c) 2018 Pokechu22, julialy
 *
 * This project is licensed under the MMPLv2.  The full text of the MMPL can be
 * found in LICENSE.md, or online at https://github.com/iopleke/MMPLv2/blob/master/LICENSE.md
 * For information about this the MMPLv2, see http://stopmodreposts.org/
 *
 * Do not redistribute (in modified or unmodified form) without prior permission.
 */
package wdl;

import java.util.function.Consumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEventData;
import net.minecraft.block.BlockState;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.ServerWorld;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.AbstractChunkProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ServerChunkProvider;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import wdl.ExtWorldClient.SimpleChunkProvider;
import wdl.ExtWorldServer.SimpleChunkProvider;


import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEventData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

abstract class ExtWorldClient extends ClientWorld {
	public ExtWorldClient(ClientPlayNetHandler netHandler, WorldSettings settings, DimensionType dim,
			Difficulty difficulty, Profiler profilerIn) {
		super(netHandler, settings, dim.getId(), difficulty, profilerIn);
	}

	@Override
	protected ClientChunkProvider createChunkProvider() {
		ClientChunkProvider provider = new SimpleChunkProvider();
		// Update WorldClient.clientChunkProvider which is a somewhat redundant field
		ReflectionUtils.findAndSetPrivateField(this, ClientWorld.class, ClientChunkProvider.class, provider);
		return provider;
	}

	@Override
	public Entity getEntityByID(int id) {
		// Ignore the WorldClient implementation which depends on mc.player
		return this.entitiesById.lookup(id);
	}

	@Override
	public boolean spawnEntity(Entity entityIn) {
		// WorldClient implementation depends on an MC instance
		int chunkX = MathHelper.floor(entityIn.posX / 16.0D);
		int chunkZ = MathHelper.floor(entityIn.posZ / 16.0D);
		this.getChunk(chunkX, chunkZ).addEntity(entityIn);
		this.loadedEntityList.add(entityIn);
		this.onEntityAdded(entityIn);
		return true;
	}

	/** Places a block, creating the proper state. */
	public void placeBlockAt(BlockPos pos, Block block, PlayerEntity player, Direction direction) {
		player.rotationYaw = direction.getHorizontalAngle();
		BlockState state = block.getStateForPlacement(this, pos, direction, 0, 0, 0, 0, player);
		this.setBlockState(pos, state);
	}

	/**
	 * A simple chunk provider implementation that just caches chunks in a map.
	 */
	private final class SimpleChunkProvider extends ClientChunkProvider implements AbstractChunkProvider {
		private final Long2ObjectMap<Chunk> map = new Long2ObjectOpenHashMap<>();

		public SimpleChunkProvider() {
			super(ExtWorldClient.this);
		}

		@Override
		public Chunk getChunk(int x, int z) {
			return map.computeIfAbsent(ChunkPos.asLong(x, z), k -> new Chunk(ExtWorldClient.this, x, z));
		}

		@Override
		public Chunk func_186026_b(int x, int z) {
			return getChunk(x, z);
		}

		@Override
		public boolean tick() {
			return false;
		}

		@Override
		public String makeString() {
			return this.toString();
		}
	}
}

abstract class ExtWorldServer extends ServerWorld {
	public ExtWorldServer(MinecraftServer server, SaveHandler saveHandlerIn, WorldInfo info, DimensionType dim,
			Profiler profilerIn) {
		super(server, saveHandlerIn, info, dim.getId(), profilerIn);
	}

	/** Places a block, creating the proper state. */
	public void placeBlockAt(BlockPos pos, Block block, PlayerEntity player, Direction direction) {
		player.rotationYaw = direction.getHorizontalAngle();
		BlockState state = block.getStateForPlacement(this, pos, direction, 0, 0, 0, 0, player);
		this.setBlockState(pos, state);
	}

	/** Left-clicks a block. */
	public void clickBlock(BlockPos pos, PlayerEntity player) {
		BlockState state = this.getBlockState(pos);
		state.getBlock().onBlockClicked(this, pos, player);
	}

	/** Right-clicks a block. */
	public void interactBlock(BlockPos pos, PlayerEntity player) {
		BlockState state = this.getBlockState(pos);
		state.getBlock().onBlockActivated(this, pos, state, player, Hand.MAIN_HAND, player.getHeldItemMainhand(), Direction.DOWN, 0, 0, 0);
	}

	/** Right-clicks an entity. */
	public void interactEntity(Entity entity, PlayerEntity player) {
		entity.processInitialInteract(player, player.getHeldItemMainhand(), Hand.MAIN_HAND);
	}

	public Consumer<BlockEventData> onBlockEvent = (e -> super.addBlockEvent(e.getPosition(), e.getBlock(), e.getEventID(), e.getEventParameter()));

	@Override
	public void addBlockEvent(BlockPos pos, Block blockIn, int eventID, int eventParam) {
		onBlockEvent.accept(new BlockEventData(pos, blockIn, eventID, eventParam));
	}

	@Override
	protected ServerChunkProvider createChunkProvider() {
		return new SimpleChunkProvider();
	}

	/**
	 * A simple chunk provider implementation that just caches chunks in a map.
	 */
	private final class SimpleChunkProvider extends ServerChunkProvider implements AbstractChunkProvider {
		private final Long2ObjectMap<Chunk> map = new Long2ObjectOpenHashMap<>();

		public SimpleChunkProvider() {
			super(ExtWorldServer.this, null, null);
		}

		@Override
		public Chunk getChunk(int x, int z) {
			return map.computeIfAbsent(ChunkPos.asLong(x, z), k -> new Chunk(ExtWorldServer.this, x, z));
		}

		@Override
		public Chunk func_186026_b(int x, int z) {
			return getChunk(x, z);
		}

		@Override
		public boolean tick() {
			return false;
		}

		@Override
		public String makeString() {
			return this.toString();
		}

		@Override
		public int getLoadedChunkCount() {
			return map.size();
		}

		@Override
		public boolean chunkExists(int x, int z) {
			return true;
		}
	}
}
