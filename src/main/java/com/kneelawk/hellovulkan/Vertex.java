package com.kneelawk.hellovulkan;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class Vertex {
	public static final int SIZEOF = 5 * 4;
	public static final int POS_OFFSET = 0;
	public static final int COLOR_OFFSET = 2 * 4;

	private Vector2f pos;
	private Vector3f color;

	public Vertex(Vector2f pos, Vector3f color) {
		this.pos = pos;
		this.color = color;
	}

	public Vector2f getPos() {
		return pos;
	}

	public Vector3f getColor() {
		return color;
	}

	public static VkVertexInputBindingDescription getBindingDescription() {
		MemoryStack stack = MemoryStack.stackGet();

		VkVertexInputBindingDescription bindingDescription = VkVertexInputBindingDescription.callocStack(stack);
		bindingDescription.binding(0);
		bindingDescription.stride(SIZEOF);
		bindingDescription.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

		return bindingDescription;
	}

	public static VkVertexInputAttributeDescription.Buffer getAttributeDescriptions() {
		MemoryStack stack = MemoryStack.stackGet();

		VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.callocStack(2, stack);

		attributeDescriptions.position(0);
		attributeDescriptions.binding(0);
		attributeDescriptions.location(0);
		attributeDescriptions.format(VK_FORMAT_R32G32_SFLOAT);
		attributeDescriptions.offset(POS_OFFSET);

		attributeDescriptions.position(1);
		attributeDescriptions.binding(0);
		attributeDescriptions.location(1);
		attributeDescriptions.format(VK_FORMAT_R32G32B32_SFLOAT);
		attributeDescriptions.offset(COLOR_OFFSET);

		attributeDescriptions.rewind();

		return attributeDescriptions;
	}

	public void writeTo(int offset, ByteBuffer buffer) {
		pos.get(offset + POS_OFFSET, buffer);
		color.get(offset + COLOR_OFFSET, buffer);
	}
}
