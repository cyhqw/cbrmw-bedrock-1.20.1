package org.batchpacket.submitchange_batchpacket;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.batchpacket.submitchange_batchpacket.AreaSelectionManager;

@EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AreaSelectionRenderer {
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        AreaSelectionManager manager = AreaSelectionManager.INSTANCE;
        BlockPos start = manager.getSelectionStart();
        BlockPos end = manager.getSelectionEnd();
        if (start == null) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(-camPos.x(), -camPos.y(), -camPos.z());
        LevelRenderer.renderLineBox((PoseStack)poseStack, (VertexConsumer)vertexConsumer, (AABB)AreaSelectionRenderer.toSingleBlockAabb(start), (float)1.0f, (float)0.9f, (float)0.2f, (float)1.0f);
        if (end != null) {
            LevelRenderer.renderLineBox((PoseStack)poseStack, (VertexConsumer)vertexConsumer, (AABB)AreaSelectionRenderer.toSelectionAabb(start, end), (float)1.0f, (float)0.0f, (float)0.0f, (float)1.0f);
        } else {
            BlockPos previewEnd = AreaSelectionRenderer.getPreviewEndPos(mc);
            if (previewEnd != null) {
                LevelRenderer.renderLineBox((PoseStack)poseStack, (VertexConsumer)vertexConsumer, (AABB)AreaSelectionRenderer.toSelectionAabb(start, previewEnd), (float)0.55f, (float)0.85f, (float)1.0f, (float)1.0f);
            }
        }
        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static BlockPos getPreviewEndPos(Minecraft mc) {
        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof BlockHitResult) {
            BlockHitResult blockHitResult = (BlockHitResult)hitResult;
            return blockHitResult.getBlockPos();
        }
        return null;
    }

    private static AABB toSingleBlockAabb(BlockPos pos) {
        return new AABB((double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)(pos.getX() + 1), (double)(pos.getY() + 1), (double)(pos.getZ() + 1));
    }

    private static AABB toSelectionAabb(BlockPos start, BlockPos end) {
        return new AABB((double)Math.min(start.getX(), end.getX()), (double)Math.min(start.getY(), end.getY()), (double)Math.min(start.getZ(), end.getZ()), (double)(Math.max(start.getX(), end.getX()) + 1), (double)(Math.max(start.getY(), end.getY()) + 1), (double)(Math.max(start.getZ(), end.getZ()) + 1));
    }
}
