package com.kneelawk.hellovulkan;

import com.google.common.math.IntMath;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.lwjgl.system.MemoryUtil.*;

public class BufferUtils {
	/*
	 * Portions of this were taken from Google Guava's com.google.common.io.ByteStreams.
	 */

	public static ByteBuffer toByteBuffer(ReadableByteChannel channel) throws IOException {
		return toByteBufferInternal(channel, new ArrayDeque<>(TO_BYTE_BUFFER_QUEUE_SIZE), 0);
	}

	private static final int BUFFER_SIZE = 8192;

	private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

	private static final int TO_BYTE_BUFFER_QUEUE_SIZE = 20;

	private static ByteBuffer toByteBufferInternal(ReadableByteChannel channel, Deque<ByteBuffer> bufs, int totalLen) throws IOException {
		for (int bufSize = BUFFER_SIZE; totalLen < MAX_BUFFER_SIZE; bufSize = IntMath.saturatedMultiply(bufSize, 2)) {
			ByteBuffer buf = memAlloc(Math.min(bufSize, MAX_BUFFER_SIZE - totalLen));
			bufs.add(buf);
			while (buf.remaining() > 0) {
				int r = channel.read(buf);
				if (r == -1) {
					buf.flip();
					return combineBuffers(bufs, totalLen);
				}
				totalLen += r;
			}
			buf.flip();
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			if (channel.read(stack.malloc(1)) == -1) {
				return combineBuffers(bufs, totalLen);
			} else {
				throw new OutOfMemoryError("input is too large to fin in a byte buffer");
			}
		}
	}

	private static ByteBuffer combineBuffers(Deque<ByteBuffer> bufs, int totalLen) {
		ByteBuffer result = memAlloc(totalLen);
		int remaining = totalLen;
		while (remaining > 0) {
			ByteBuffer buf = bufs.removeFirst();
			int resultOffset = totalLen - remaining;
			result.position(resultOffset);
			// the sum of all the remaining bytes in all the buffers in bufs will always add up to totalLen
			memCopy(buf, result);
			remaining -= buf.remaining();
			memFree(buf);
		}
		result.rewind();
		return result;
	}
}
