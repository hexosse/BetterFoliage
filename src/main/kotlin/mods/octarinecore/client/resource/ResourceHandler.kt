package mods.octarinecore.client.resource

import cpw.mods.fml.client.event.ConfigChangedEvent
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import mods.octarinecore.client.render.Double3
import mods.octarinecore.client.render.Int3
import mods.octarinecore.client.render.Model
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.util.IIcon
import net.minecraft.util.MathHelper
import net.minecraft.util.ResourceLocation
import net.minecraft.world.World
import net.minecraft.world.gen.NoiseGeneratorSimplex
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.world.WorldEvent
import java.util.*

// ============================
// Resource types
// ============================
interface IStitchListener { fun onStitch(atlas: IIconRegister) }
interface IConfigChangeListener { fun onConfigChange() }
interface IWorldLoadListener { fun onWorldLoad(world: World) }

/**
 * Base class for declarative resource handling.
 *
 * Resources are automatically reloaded/recalculated when the appropriate events are fired.
 *
 * @param[modId] mod ID associated with this handler (used to filter config change events)
 */
open class ResourceHandler(val modId: String) {

    val resources = linkedListOf<Any>()
    open fun afterStitch() {}

    // ============================
    // Self-registration
    // ============================
    init {
        MinecraftForge.EVENT_BUS.register(this)
        FMLCommonHandler.instance().bus().register(this)
    }

    // ============================
    // Resource declarations
    // ============================
    fun iconStatic(domain: String, path: String) = IconHolder(domain, path).apply { resources.add(this) }
    fun iconStatic(location: ResourceLocation) = iconStatic(location.resourceDomain, location.resourcePath)
    fun iconSet(domain: String, pathPattern: String) = IconSet(domain, pathPattern).apply { resources.add(this) }
    fun iconSet(location: ResourceLocation) = iconSet(location.resourceDomain, location.resourcePath)
    fun model(init: Model.()->Unit) = ModelHolder(init).apply { resources.add(this) }
    fun modelSet(num: Int, init: Model.(Int)->Unit) = ModelSet(num, init).apply { resources.add(this) }
    fun vectorSet(num: Int, init: (Int)-> Double3) = VectorSet(num, init).apply { resources.add(this) }
    fun simplexNoise() = SimplexNoise().apply { resources.add(this) }

    // ============================
    // Event registration
    // ============================
    @SubscribeEvent
    fun onStitch(event: TextureStitchEvent.Pre) {
        if (event.map.textureType == 0) {
            resources.forEach { (it as? IStitchListener)?.onStitch(event.map) }
            afterStitch()
        }
    }

    @SubscribeEvent
    fun handleConfigChange(event: ConfigChangedEvent.OnConfigChangedEvent) {
        if (event.modID == modId) resources.forEach { (it as? IConfigChangeListener)?.onConfigChange() }
    }

    @SubscribeEvent
    fun handleWorldLoad(event: WorldEvent.Load) =
        resources.forEach { (it as? IWorldLoadListener)?.onWorldLoad(event.world) }
}

// ============================
// Resource container classes
// ============================
class IconHolder(val domain: String, val name: String) : IStitchListener {
    var icon: IIcon? = null
    override fun onStitch(atlas: IIconRegister) { icon = atlas.registerIcon("$domain:$name") }
}

class ModelHolder(val init: Model.()->Unit): IConfigChangeListener {
    var model: Model = Model().apply(init)
    override fun onConfigChange() { model = Model().apply(init) }
}

class IconSet(val domain: String, val namePattern: String) : IStitchListener {
    val icons = arrayOfNulls<IIcon>(16)
    var num = 0

    override fun onStitch(atlas: IIconRegister) {
        num = 0;
        (0..15).forEach { idx ->
            val locReal = ResourceLocation(domain, "textures/blocks/${namePattern.format(idx)}.png")
            if (resourceManager[locReal] != null) icons[num++] = atlas.registerIcon("$domain:${namePattern.format(idx)}")
        }
    }

    operator fun get(idx: Int) = if (num == 0) null else icons[idx % num]
}

class ModelSet(val num: Int, val init: Model.(Int)->Unit): IConfigChangeListener {
    val models = Array(num) { Model().apply{ init(it) } }
    override fun onConfigChange() { (0..num-1).forEach { models[it] = Model().apply{ init(it) } } }
    operator fun get(idx: Int) = models[idx % num]
}

class VectorSet(val num: Int, val init: (Int)->Double3): IConfigChangeListener {
    val models = Array(num) { init(it) }
    override fun onConfigChange() { (0..num-1).forEach { models[it] = init(it) } }
    operator fun get(idx: Int) = models[idx % num]
}

class SimplexNoise() : IWorldLoadListener {
    var noise = NoiseGeneratorSimplex()
    override fun onWorldLoad(world: World) { noise = NoiseGeneratorSimplex(Random(world.worldInfo.seed))
    }
    operator fun get(x: Int, z: Int) = MathHelper.floor_double((noise.func_151605_a(x.toDouble(), z.toDouble()) + 1.0) * 32.0)
    operator fun get(pos: Int3) = get(pos.x, pos.z)
}