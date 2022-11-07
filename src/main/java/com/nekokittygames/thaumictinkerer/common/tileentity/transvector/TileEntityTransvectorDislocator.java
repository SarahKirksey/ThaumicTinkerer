package com.nekokittygames.thaumictinkerer.common.tileentity.transvector;

import com.nekokittygames.thaumictinkerer.common.blocks.transvector.BlockTransvectorDislocator;
import com.nekokittygames.thaumictinkerer.common.config.TTConfig;
import com.nekokittygames.thaumictinkerer.common.misc.APIHelpers;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.common.lib.utils.BlockUtils;

import javax.annotation.Nonnull;
import java.util.List;

public class TileEntityTransvectorDislocator extends TileEntityTransvector {

    private int cooldown = 0;
    private boolean pulseStored = false;
    @Override
    public void readExtraNBT(NBTTagCompound compound) {
        super.readExtraNBT(compound);
        if (compound.hasKey("cooldown"))
            cooldown = compound.getInteger("cooldown");
    }

    @Override
    public boolean respondsToPulses() {
        return true;
    }

    @Override
    public void writeExtraNBT(NBTTagCompound compound) {
        super.writeExtraNBT(compound);
        compound.setInteger("cooldown", cooldown);
    }

    @Override
    public void update() {
        super.update();

        cooldown = Math.max(0, cooldown - 1);
        if (cooldown == 0 && pulseStored) {
            pulseStored=false;
            activateOnPulse();
        }
    }

    @Override
    public void activateOnPulse() {
        super.activateOnPulse();
        if(!world.isRemote) {
            getTile(); // Sanity check!
            if (getTilePos() == null)
                return;

            if (cooldown > 0) {
                pulseStored = true;
                return;
            }

            BlockPos targetCoords = getBlockTarget();
            if (!world.isAirBlock(getTilePos())) {
                BlockData endData = new BlockData(getTilePos());
                BlockData targetData = new BlockData(targetCoords);

                if (checkBlock(targetCoords) && checkBlock(getTilePos())) {
                    endData.clearTileEntityAt();
                    targetData.clearTileEntityAt();

                    endData.setTo(targetCoords);
                    targetData.setTo(getTilePos());

                }
            }

            List<Entity> entitiesAtEnd = getEntitiesAtPoint(getTilePos());
            List<Entity> entitiesAtTarget = getEntitiesAtPoint(targetCoords);

            for (Entity entity : entitiesAtEnd)
                moveEntity(entity, targetCoords);

            for (Entity entity : entitiesAtTarget)
                moveEntity(entity, getTilePos());
        }
        cooldown = 10;
    }


    private void moveEntity(Entity entity, BlockPos pos) {
        if (entity == null)
            return;
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            player.connection.setPlayerLocation(pos.getX(), pos.getY(), pos.getZ(), player.rotationYaw, player.rotationPitch);
        } else
            entity.setPosition(pos.getX(), pos.getY(), pos.getZ());
    }

    private List<Entity> getEntitiesAtPoint(BlockPos coords) {
        return world.getEntitiesWithinAABB(Entity.class, world.getBlockState(coords).getBoundingBox(world, coords));
    }

    private boolean checkBlock(BlockPos coords) {
        IBlockState state = world.getBlockState(coords);
        return (!BlockUtils.isPortableHoleBlackListed(state) && APIHelpers.canDislocateBlock(world,state.getBlock(),coords));
    }

    private BlockPos getBlockTarget() {
        EnumFacing dir = world.getBlockState(pos).getValue(BlockTransvectorDislocator.FACING);
        return pos.offset(dir);
        //return new ChunkCoordinates(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
    }

    @Override
    public void validate() {
        super.validate();
        setCheaty(true);
    }

    @Override
    public int getMaxDistance() {
        return TTConfig.transvectorDislocatorDistance * TTConfig.transvectorDislocatorDistance;
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, @Nonnull IBlockState oldState, @Nonnull IBlockState newSate) {
        if (oldState.getBlock() == newSate.getBlock())
            return false;
        else
            return super.shouldRefresh(world, pos, oldState, newSate);
    }

    class BlockData {
        private IBlockState state;
        private NBTTagCompound tile;
        private BlockPos pos;

        BlockData(IBlockState state, TileEntity tile, BlockPos pos) {
            this.state = state;
            if (tile != null) {
                NBTTagCompound cmp = new NBTTagCompound();
                tile.writeToNBT(cmp);
                this.tile = cmp;
            }
            this.pos = pos;
        }

        BlockData(BlockPos pos) {
            this(world.getBlockState(pos), world.getTileEntity(pos), pos);
        }

        protected void clearTileEntityAt() {
            if (state != null) {
                TileEntity tileToSet = state.getBlock().createTileEntity(world, state);
                world.setTileEntity(pos, tileToSet);
            }
        }

        public void setTo(BlockPos pos) {
            world.setBlockState(pos, state, 1 | 2);
            TileEntity tile = this.tile == null ? null : TileEntity.create(world, this.tile);
            world.setTileEntity(pos, tile);
            if (tile != null) {
                tile.setPos(pos);
                tile.updateContainingBlockInfo();
            }
            if (state != null) {
                state.getBlock().onNeighborChange(world, pos, getPos());
            }

        }

    }
}
