package universalmod.utils.render.ui.liquidglass;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.utils.render.ScissorUtil;
import universalmod.utils.render.ui.Render2DCoordinateSpace;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class SquircleRenderer implements AutoCloseable {
    private static final int MAX=512, PARAMS=3, BYTES=MAX*PARAMS*4*Float.BYTES;
    private static volatile SquircleRenderer instance;
    public static final RenderPipeline PIPELINE=RenderPipeline.builder()
            .withLocation(id("pipeline/squircle"))
            .withVertexShader(id("ui/liquidglass/squircle"))
            .withFragmentShader(id("ui/liquidglass/squircle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withCull(false)
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("SquircleParamsArray", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER).build();
    private final List<BuiltSquircle> prepared=new ArrayList<>(64);
    private GuiGraphics graphics; private GpuBuffer buffer; private boolean dirty=true;
    private SquircleRenderer(){}
    public static SquircleRenderer getInstance(){ var l=instance; if(l==null){ synchronized(SquircleRenderer.class){ l=instance; if(l==null) instance=l=new SquircleRenderer(); }} return l; }
    public static void closeInstance(){ var l=instance; if(l!=null){l.close();instance=null;} }
    public void beginFrame(GuiGraphics g){graphics=g;} public void flush(){graphics=null;} public void beginGuiFrame(){prepared.clear();dirty=false;}
    public void enqueue(BuiltSquircle s){ if(graphics==null||s==null||!s.visible())return; Matrix3x2f p= Render2DCoordinateSpace.pose(graphics); ((GuiGraphicsExtractorAccessor)graphics).universalmod$getGuiRenderState().submitGuiElement(new SquircleRenderState(p,normalize(s), ScissorUtil.current())); }
    int reserve(BuiltSquircle s){int i=prepared.size();if(i>=MAX)return-1;prepared.add(s);dirty=true;return i;}
    public boolean isPipeline(RenderPipeline p){return p==PIPELINE;}
    public void bindParams(RenderPass p){if(p==null||prepared.isEmpty())return;GpuBuffer b=ensure();if(b!=null)p.setUniform("SquircleParamsArray",b);}
    public void prepareBuffers(){if(prepared.isEmpty()||!dirty)return;GpuBuffer b=ensureWritable();if(b==null)return;try(MemoryStack st=MemoryStack.stackPush()){ByteBuffer d=data(st);RenderSystem.getDevice().createCommandEncoder().writeToBuffer(b.slice(0,d.remaining()),d);dirty=false;}catch(RuntimeException ignored){dirty=true;}}
    private ByteBuffer data(MemoryStack st){ByteBuffer d=st.calloc(Math.max(1,prepared.size())*PARAMS*16);for(int i=0;i<prepared.size();i++){var s=prepared.get(i);int o=i*PARAMS*16;d.putFloat(o,s.radiusTopLeft());d.putFloat(o+4,s.radiusBottomLeft());d.putFloat(o+8,s.radiusTopRight());d.putFloat(o+12,s.radiusBottomRight());d.putFloat(o+16,s.width());d.putFloat(o+20,s.height());d.putFloat(o+24,0.5f);d.putFloat(o+28,s.squirt());d.putFloat(o+32,s.z());}d.position(0);return d;}
    private BuiltSquircle normalize(BuiltSquircle s){return new BuiltSquircle(s.x(),s.y(),s.width(),s.height(),Math.max(0.0f,s.radiusTopLeft()),Math.max(0.0f,s.radiusTopRight()),Math.max(0.0f,s.radiusBottomRight()),Math.max(0.0f,s.radiusBottomLeft()),Math.max(.001f,s.squirt()),s.color(),s.z());}
    private GpuBuffer ensure(){if(!dirty&&buffer!=null)return buffer;prepareBuffers();if(!dirty&&buffer!=null)return buffer;closeBuffer();try(MemoryStack st=MemoryStack.stackPush()){ByteBuffer d=data(st);buffer=RenderSystem.getDevice().createBuffer(()->"UNIVERSALMOD_squircle_params",GpuBuffer.USAGE_UNIFORM,d);dirty=false;return buffer;}}
    private GpuBuffer ensureWritable(){if(buffer!=null&&!buffer.isClosed()&&buffer.size()>=BYTES)return buffer;closeBuffer();try{return buffer=RenderSystem.getDevice().createBuffer(()->"UNIVERSALMOD_squircle_params",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,BYTES);}catch(RuntimeException e){return null;}}
    private void closeBuffer(){if(buffer!=null){buffer.close();buffer=null;}} private static Identifier id(String p){return Identifier.fromNamespaceAndPath("universalmod",p);} @Override public void close(){prepared.clear();graphics=null;closeBuffer();}
}
