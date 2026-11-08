package client.testbundle

import common.testbundle.BlockEntityDemoEntrypoint
import common.testbundle.CommonEntrypoint
import de.luckymcdev.foundryengine.Common
import de.luckymcdev.foundryengine.event.common.BundleEvents
import de.luckymcdev.foundryengine.event.common.ClientEvents
import de.luckymcdev.foundryengine.render.client.blockentity.EngineBlockEntityRenderers
import de.luckymcdev.foundryengine.script.common.BundleEntrypoint
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

class ClientEntrypoint implements BundleEntrypoint {

	@Override
	void onLoad() {
		ClientEvents.tick {
			def player = Minecraft.getInstance().player
			if (player != null) {
				if (player.tickCount % 20 == 0) {
					//player.sendSystemMessage(Component.literal("Test + " + player.tickCount))
				}
			}
		}

		ClientEvents.renderGui {
			it.guiGraphics.fakeItem(new ItemStack(CommonEntrypoint.THIS_IS_A_ITEM.get()), 100, 200)
		}

		BundleEvents.registry {
			BlockEntityDemoEntrypoint.COUNTER_BE.renderer(EngineBlockEntityRenderers.noop())
		}

		Common.LOGGER.error("Test Load Client")
	}

	@Override
	void onUnload() {
	}
}