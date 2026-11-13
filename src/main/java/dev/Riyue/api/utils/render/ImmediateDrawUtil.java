package dev.Riyue.api.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class ImmediateDrawUtil {
	private static final ProjectionMatrix2 GUI_PROJECTION_MATRIX = new ProjectionMatrix2("riyue_gui", 1000.0F, 11000.0F, true);
	private static float colorModulatorAlpha = 1.0F;
	private static final RenderPipeline POS_COLOR_QUADS_NO_DEPTH = registerPosColorNoDepth("quads", VertexFormat.DrawMode.QUADS);
	private static final RenderPipeline POS_COLOR_LINES_NO_DEPTH = registerPosColorNoDepth("lines", VertexFormat.DrawMode.LINES);
	private static final RenderPipeline POS_COLOR_DEBUG_LINES_NO_DEPTH = registerPosColorNoDepth("debug_lines", VertexFormat.DrawMode.DEBUG_LINES);
	private static final RenderPipeline POS_COLOR_DEBUG_LINE_STRIP_NO_DEPTH = registerPosColorNoDepth("debug_line_strip", VertexFormat.DrawMode.DEBUG_LINE_STRIP);
	private static final RenderPipeline POS_COLOR_TRIANGLE_FAN_NO_DEPTH = registerPosColorNoDepth("triangle_fan", VertexFormat.DrawMode.TRIANGLE_FAN);
	private static final RenderPipeline POS_COLOR_TRIANGLE_STRIP_NO_DEPTH = registerPosColorNoDepth("triangle_strip", VertexFormat.DrawMode.TRIANGLE_STRIP);
	private static final RenderPipeline POS_TEX_COLOR_QUADS_NO_DEPTH = registerPosTexColorNoDepth("quads", VertexFormat.DrawMode.QUADS);
	private static final RenderPipeline POS_TEX_COLOR_LINES_NO_DEPTH = registerPosTexColorNoDepth("lines", VertexFormat.DrawMode.LINES);
	private static final RenderPipeline POS_TEX_COLOR_DEBUG_LINES_NO_DEPTH = registerPosTexColorNoDepth("debug_lines", VertexFormat.DrawMode.DEBUG_LINES);
	private static final RenderPipeline POS_TEX_COLOR_DEBUG_LINE_STRIP_NO_DEPTH = registerPosTexColorNoDepth("debug_line_strip", VertexFormat.DrawMode.DEBUG_LINE_STRIP);
	private static final RenderPipeline POS_TEX_COLOR_TRIANGLE_FAN_NO_DEPTH = registerPosTexColorNoDepth("triangle_fan", VertexFormat.DrawMode.TRIANGLE_FAN);
	private static final RenderPipeline POS_TEX_COLOR_TRIANGLE_STRIP_NO_DEPTH = registerPosTexColorNoDepth("triangle_strip", VertexFormat.DrawMode.TRIANGLE_STRIP);

	private static final RenderPipeline LINES_WIDE_NO_DEPTH = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
					.withLocation(Identifier.of("riyueclient", "pipeline/lines_wide_no_depth"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.build()
	);

	private ImmediateDrawUtil() {
	}

	public static void setColorModulatorAlpha(float alpha) {
		colorModulatorAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
	}

	public static void resetColorModulator() {
		colorModulatorAlpha = 1.0F;
	}

	private static RenderPipeline registerPosColorNoDepth(String name, VertexFormat.DrawMode drawMode) {
		return RenderPipelines.register(
				RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
						.withLocation(Identifier.of("riyueclient", "pipeline/pos_color_no_depth_" + name))
						.withCull(false)
						.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
						.withDepthWrite(false)
						.withVertexFormat(VertexFormats.POSITION_COLOR, drawMode)
						.build()
		);
	}

	private static RenderPipeline registerPosTexColorNoDepth(String name, VertexFormat.DrawMode drawMode) {
		return RenderPipelines.register(
				RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
						.withLocation(Identifier.of("riyueclient", "pipeline/pos_tex_color_no_depth_" + name))
						.withCull(false)
						.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
						.withDepthWrite(false)
						.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, drawMode)
						.build()
		);
	}

	public static void drawGui(BufferBuilder builder) {
		enqueue(builder, null, true);
	}

	public static void drawGuiTextured(BufferBuilder builder, GpuTextureView textureView) {
		enqueue(builder, textureView, true);
	}

	public static void drawWorld(BufferBuilder builder) {
		enqueue(builder, null, false);
	}

	public static void drawWorldTextured(BufferBuilder builder, GpuTextureView textureView) {
		enqueue(builder, textureView, false);
	}

	// ---------------------------------------------------------------------
	// 合批渲染：每个矩形/文字不再单独开一次 RenderPass（那是一帧几百次通道切换，
	// 严重拖帧率），而是先把顶点数据攒起来，按插入顺序在帧内的固定刷新点统一提交。
	// 相同 (格式+纹理) 的连续 QUADS 顶点直接追加进同一个批次（QUADS 的索引是
	// 平铺重复模式，可以安全合并）；strip/fan/lines 等拓扑敏感的模式保持独立
	// 绘制项但同样延迟到刷新点，省掉每次 backupProjectionMatrix/建通道的开销。
	// ---------------------------------------------------------------------

	/** 待提交项达到该数量时自动刷新（提前提交总是保持绘制顺序，只是提前画而已）。 */
	private static final int AUTO_FLUSH_LIMIT = 160;

	private static final List<Object> PENDING = new ArrayList<>();
	private static PendingQuadsBatch lastQuadsBatch;

	/** GUI 项共用的模型视图（向下平移 11000 进入正交投影的深度范围）。 */
	private static final Matrix4f GUI_MODEL_VIEW = new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F);
	private static final Matrix4f WORLD_MODEL_VIEW = new Matrix4f();

	private static final class PendingQuadsBatch {
		final VertexFormat format;
		final GpuTextureView texture;
		final boolean gui;
		final float alpha;
		ByteBuffer data;
		int vertexCount;

		PendingQuadsBatch(VertexFormat format, GpuTextureView texture, boolean gui, float alpha) {
			this.format = format;
			this.texture = texture;
			this.gui = gui;
			this.alpha = alpha;
			this.data = ByteBuffer.allocateDirect(4096);
		}

		void append(ByteBuffer src, int verts) {
			int needed = data.position() + src.remaining();
			if (needed > data.capacity()) {
				int newCap = Math.max(data.capacity() * 2, needed);
				ByteBuffer nb = ByteBuffer.allocateDirect(newCap);
				data.flip();
				nb.put(data);
				data = nb;
			}
			data.put(src);
			vertexCount += verts;
		}
	}

	private static final class PendingSingle {
		final VertexFormat format;
		final VertexFormat.DrawMode mode;
		final GpuTextureView texture;
		final boolean gui;
		final float alpha;
		final ByteBuffer data;
		final int indexCount;

		PendingSingle(VertexFormat format, VertexFormat.DrawMode mode, GpuTextureView texture, boolean gui, float alpha,
				ByteBuffer data, int indexCount) {
			this.format = format;
			this.mode = mode;
			this.texture = texture;
			this.gui = gui;
			this.alpha = alpha;
			this.data = data;
			this.indexCount = indexCount;
		}
	}

	private static void enqueue(BufferBuilder builder, GpuTextureView textureView, boolean gui) {
		BuiltBuffer builtBuffer = builder.endNullable();
		if (builtBuffer == null) {
			return;
		}
		try {
			BuiltBuffer.DrawParameters parameters = builtBuffer.getDrawParameters();
			ByteBuffer src = builtBuffer.getBuffer();
			float alpha = colorModulatorAlpha;

			if (parameters.mode() == VertexFormat.DrawMode.QUADS && parameters.vertexCount() > 0) {
				if (lastQuadsBatch != null
						&& lastQuadsBatch.format == parameters.format()
						&& lastQuadsBatch.texture == textureView
						&& lastQuadsBatch.gui == gui
						&& lastQuadsBatch.alpha == alpha
						&& !PENDING.isEmpty() && PENDING.get(PENDING.size() - 1) == lastQuadsBatch) {
					lastQuadsBatch.append(src, parameters.vertexCount());
				} else {
					PendingQuadsBatch batch = new PendingQuadsBatch(parameters.format(), textureView, gui, alpha);
					batch.append(src, parameters.vertexCount());
					PENDING.add(batch);
					lastQuadsBatch = batch;
				}
			} else if (parameters.vertexCount() > 0) {
				ByteBuffer copy = ByteBuffer.allocateDirect(src.remaining());
				copy.put(src).flip();
				PENDING.add(new PendingSingle(parameters.format(), parameters.mode(), textureView, gui, alpha,
						copy, parameters.indexCount()));
				lastQuadsBatch = null;
			}
		} finally {
			builtBuffer.close();
		}
		if (PENDING.size() >= AUTO_FLUSH_LIMIT) {
			flushBatches();
		}
	}

	/** 把攒好的绘制项按顺序一次性提交。必须在每段渲染逻辑的末尾调用以保持绘制顺序。 */
	public static void flushBatches() {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Object> items = new ArrayList<>(PENDING);
		PENDING.clear();
		lastQuadsBatch = null;

		Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
		boolean inGuiRun = false;
		try {
			for (Object item : items) {
				boolean gui = item instanceof PendingQuadsBatch ? ((PendingQuadsBatch) item).gui : ((PendingSingle) item).gui;
				if (gui && !inGuiRun) {
					RenderSystem.backupProjectionMatrix();
					applyGuiProjection();
					inGuiRun = true;
				} else if (!gui && inGuiRun) {
					RenderSystem.restoreProjectionMatrix();
					inGuiRun = false;
				}

				PendingQuadsBatch batch;
				PendingSingle single;
				if (item instanceof PendingQuadsBatch q) {
					batch = q;
					ByteBuffer data = batch.data;
					data.flip();
					int indexCount = (batch.vertexCount / 4) * 6;
					renderBatch(batch.format, VertexFormat.DrawMode.QUADS, batch.texture, batch.gui, batch.alpha,
							data, indexCount, framebuffer);
				} else {
					single = (PendingSingle) item;
					renderBatch(single.format, single.mode, single.texture, single.gui, single.alpha,
							single.data, single.indexCount, framebuffer);
				}
			}
		} finally {
			if (inGuiRun) {
				RenderSystem.restoreProjectionMatrix();
			}
		}
	}

	private static void applyGuiProjection() {
		MinecraftClient client = MinecraftClient.getInstance();
		Window window = client.getWindow();
		RenderSystem.setProjectionMatrix(
				GUI_PROJECTION_MATRIX.set((float) window.getFramebufferWidth() / window.getScaleFactor(), (float) window.getFramebufferHeight() / window.getScaleFactor()),
				ProjectionType.ORTHOGRAPHIC
		);
	}

	private static void renderBatch(VertexFormat format, VertexFormat.DrawMode mode, GpuTextureView textureView,
			boolean gui, float alpha, ByteBuffer vertexData, int indexCount, Framebuffer framebuffer) {
		RenderPipeline pipeline = pipelineFor(mode, format);
		RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(mode);
		GpuBuffer indexBuffer = shapeIndexBuffer.getIndexBuffer(indexCount);
		VertexFormat.IndexType indexType = shapeIndexBuffer.getIndexType();

		Matrix4f model = gui ? GUI_MODEL_VIEW : WORLD_MODEL_VIEW;
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.write(model, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), new Matrix4f());
		GpuBuffer vertexBuffer = pipeline.getVertexFormat().uploadImmediateVertexBuffer(vertexData);

		GpuTextureView colorView = RenderSystem.outputColorTextureOverride != null
				? RenderSystem.outputColorTextureOverride
				: framebuffer.getColorAttachmentView();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "riyue_immediate_nodepth", colorView, OptionalInt.empty(), null, OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.setPipeline(pipeline);
			if (textureView != null) {
				renderPass.bindTexture("Sampler0", textureView, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
			}
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.setIndexBuffer(indexBuffer, indexType);
			renderPass.drawIndexed(0, 0, indexCount, 1);
		}
	}

	private static RenderPipeline pipelineFor(VertexFormat.DrawMode drawMode, VertexFormat format) {
		if (format == VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH) {
			return LINES_WIDE_NO_DEPTH;
		}

		if (format == VertexFormats.POSITION_TEXTURE_COLOR) {
			return switch (drawMode) {
				case QUADS -> POS_TEX_COLOR_QUADS_NO_DEPTH;
				case LINES -> POS_TEX_COLOR_LINES_NO_DEPTH;
				case DEBUG_LINES -> POS_TEX_COLOR_DEBUG_LINES_NO_DEPTH;
				case DEBUG_LINE_STRIP -> POS_TEX_COLOR_DEBUG_LINE_STRIP_NO_DEPTH;
				case TRIANGLE_FAN -> POS_TEX_COLOR_TRIANGLE_FAN_NO_DEPTH;
				case TRIANGLE_STRIP -> POS_TEX_COLOR_TRIANGLE_STRIP_NO_DEPTH;
				default -> throw new IllegalArgumentException("Unsupported no-depth draw mode for POS_TEX_COLOR: " + drawMode);
			};
		}

		return switch (drawMode) {
			case QUADS -> POS_COLOR_QUADS_NO_DEPTH;
			case LINES -> POS_COLOR_LINES_NO_DEPTH;
			case DEBUG_LINES -> POS_COLOR_DEBUG_LINES_NO_DEPTH;
			case DEBUG_LINE_STRIP -> POS_COLOR_DEBUG_LINE_STRIP_NO_DEPTH;
			case TRIANGLE_FAN -> POS_COLOR_TRIANGLE_FAN_NO_DEPTH;
			case TRIANGLE_STRIP -> POS_COLOR_TRIANGLE_STRIP_NO_DEPTH;
			default -> throw new IllegalArgumentException("Unsupported no-depth draw mode: " + drawMode);
		};
	}

	public static void flushRender() {
		flushBatches();
		MinecraftClient client = MinecraftClient.getInstance();
		client.gameRenderer.guiRenderer.render(client.gameRenderer.fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
	}
}
