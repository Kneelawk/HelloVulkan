package com.kneelawk.hellovulkan;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class HelloVulkanApplication {
	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("com.kneelawk.hellovulkan.Debug", "true"));
	private static final int WINDOW_WIDTH = 1280;
	private static final int WINDOW_HEIGHT = 720;
	private static final int MAX_FRAMES_IN_FLIGHT = 2;
	private static final String[] LAYERS = {
			"VK_LAYER_LUNARG_standard_validation"
	};
	private static final String[] DEBUG_EXTENSIONS = {
			VK_EXT_DEBUG_UTILS_EXTENSION_NAME
	};
	private static final String[] DEVICE_EXTENSIONS = {
			VK_KHR_SWAPCHAIN_EXTENSION_NAME
	};

	/*
	 * GLFW stuff
	 */
	private long window;

	/*
	 * Vulkan stuff
	 */
	private VkInstance instance;

	// debug
	private long debugUtilsMessenger;

	// surface
	private long surface;

	// device
	private VkPhysicalDevice physicalDevice;
	private VkDevice device;

	// queues
	private VkQueue graphicsQueue;
	private VkQueue presentQueue;

	// swap chain
	private long swapChain;
	private List<Long> swapChainImages = Lists.newArrayList();
	private int swapChainImageFormat;
	private VkExtent2D swapChainExtent = VkExtent2D.mallocStack();
	private List<Long> swapChainImageViews = Lists.newArrayList();

	// render pass
	private long renderPass;
	private long pipelineLayout;
	private long graphicsPipeline;

	// framebuffer
	private List<Long> swapChainFramebuffers = Lists.newArrayList();

	// command buffers
	private long commandPool;
	private List<VkCommandBuffer> commandBuffers = Lists.newArrayList();

	// synchronization
	private long[] imageAvailableSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
	private long[] renderFinishedSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
	private long[] inFlightFences = new long[MAX_FRAMES_IN_FLIGHT];
	private int currentFrame = 0;

	public void run() {
		initWindow();
		initVulkan();
		mainLoop();
		cleanup();
	}

	private void initWindow() {
		glfwInit();
		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

		window = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Hello Vulkan", NULL, NULL);
	}

	private void initVulkan() {
		checkExtensions();

		if (DEBUG) {
			checkLayers();
		}

		createInstance();

		if (DEBUG) {
			setupDebugCallback();
		}

		createSurface();
		pickPhysicalDevice();
		createLogicalDevice();
		createSwapChain();
		createImageViews();
		createRenderPass();
		createGraphicsPipeline();
		createFramebuffers();
		createCommandPool();
		createCommandBuffers();
		createSyncObjects();
	}

	private void checkExtensions() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer extensionCountBuffer = stack.callocInt(1);
			vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCountBuffer, null);

			VkExtensionProperties.Buffer extensionPropertiesBuffer = VkExtensionProperties.mallocStack(extensionCountBuffer.get(0), stack);
			vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCountBuffer, extensionPropertiesBuffer);

			List<String> requiredExtensions = getRequiredExtensions();

			System.out.println("Required Extensions: " + requiredExtensions);

			System.out.println(extensionCountBuffer.get(0) + " extensions found:");
			for (VkExtensionProperties extensionProperties : extensionPropertiesBuffer) {
				int specVersion = extensionProperties.specVersion();
				System.out.println("\t" + extensionProperties.extensionNameString() + " v"
						+ VK_VERSION_MAJOR(specVersion) + "." + VK_VERSION_MINOR(specVersion) + "." + VK_VERSION_PATCH(specVersion));
				requiredExtensions.remove(extensionProperties.extensionNameString());
			}

			if (requiredExtensions.size() > 0) {
				throw new RuntimeException("Missing required extensions: " + requiredExtensions);
			}
		}
	}

	private List<String> getRequiredExtensions() {
		List<String> requiredExtensions = Lists.newArrayList();
		PointerBuffer requiredExtensionsBuffer = glfwGetRequiredInstanceExtensions();

		if (requiredExtensionsBuffer == null) {
			throw new RuntimeException("Failed to find the vulkan extensions required for GLFW");
		}

		for (int i = 0; i < requiredExtensionsBuffer.remaining(); i++) {
			requiredExtensions.add(requiredExtensionsBuffer.getStringASCII(i));
		}

		if (DEBUG) {
			requiredExtensions.addAll(Arrays.asList(DEBUG_EXTENSIONS));
		}

		return requiredExtensions;
	}

	private void checkLayers() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer layerCountBuffer = stack.callocInt(1);
			vkEnumerateInstanceLayerProperties(layerCountBuffer, null);

			VkLayerProperties.Buffer layerPropertiesBuffer = VkLayerProperties.mallocStack(layerCountBuffer.get(0), stack);
			vkEnumerateInstanceLayerProperties(layerCountBuffer, layerPropertiesBuffer);

			List<String> requiredLayers = Lists.newArrayList(LAYERS);

			System.out.println("Required Layers: " + requiredLayers);

			System.out.println(layerCountBuffer.get(0) + " layers found:");
			for (VkLayerProperties layerProperties : layerPropertiesBuffer) {
				int specVersion = layerProperties.specVersion();
				System.out.println("\t" + layerProperties.layerNameString() + " v"
						+ VK_VERSION_MAJOR(specVersion) + "." + VK_VERSION_MINOR(specVersion) + "." + VK_VERSION_PATCH(specVersion));
				requiredLayers.remove(layerProperties.layerNameString());
			}

			if (requiredLayers.size() > 0) {
				throw new RuntimeException("Missing required layers: " + requiredLayers);
			}
		}
	}

	private void createInstance() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkApplicationInfo applicationInfo = VkApplicationInfo.callocStack(stack);
			applicationInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
			applicationInfo.pApplicationName(stack.ASCII("Hello Vulkan"));
			applicationInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
			applicationInfo.pEngineName(stack.ASCII("No Engine"));
			applicationInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
			applicationInfo.apiVersion(VK_API_VERSION_1_0);

			VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo.callocStack(stack);
			instanceCreateInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
			instanceCreateInfo.pApplicationInfo(applicationInfo);

			List<String> extensions = getRequiredExtensions();
			PointerBuffer extensionsBuffer = stack.mallocPointer(extensions.size());
			for (int i = 0; i < extensions.size(); i++) {
				extensionsBuffer.put(i, stack.ASCII(extensions.get(i)));
			}

			instanceCreateInfo.ppEnabledExtensionNames(extensionsBuffer);

			if (DEBUG) {
				PointerBuffer layersBuffer = stack.mallocPointer(LAYERS.length);
				for (int i = 0; i < LAYERS.length; i++) {
					layersBuffer.put(i, stack.ASCII(LAYERS[i]));
				}

				instanceCreateInfo.ppEnabledLayerNames(layersBuffer);
			}

			PointerBuffer instanceBuf = stack.mallocPointer(1);
			if (vkCreateInstance(instanceCreateInfo, null, instanceBuf) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create VkInstance");
			}

			instance = new VkInstance(instanceBuf.get(0), instanceCreateInfo);
		}
	}

	private void setupDebugCallback() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkDebugUtilsMessengerCreateInfoEXT messengerCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);
			messengerCreateInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
			messengerCreateInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT
					| VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
			messengerCreateInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
					| VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
			messengerCreateInfo.pfnUserCallback(this::debugMessageCallback);
			messengerCreateInfo.pUserData(NULL);

			LongBuffer debugUtilsMessengerBuffer = stack.mallocLong(1);
			if (vkCreateDebugUtilsMessengerEXT(instance, messengerCreateInfo, null, debugUtilsMessengerBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create debug messenger");
			}
			debugUtilsMessenger = debugUtilsMessengerBuffer.get(0);
		}
	}

	private int debugMessageCallback(int messageSeverity, int messageType, long pCallbackData, long pUserData) {
		String severity = "";
		switch (messageSeverity) {
			case VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT:
				severity = "VERB";
				break;
			case VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT:
				severity = "INFO";
				break;
			case VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT:
				severity = "WARN";
				break;
			case VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT:
				severity = "ERROR";
				break;
		}

		String type = "";
		switch (messageType) {
			case VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT:
				type = "GENERAL";
				break;
			case VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT:
				type = "VALIDATION";
				break;
			case VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT:
				type = "PERFORMANCE";
				break;
		}

		VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);

		System.out.printf("[%s][%s] %s\n", severity, type, callbackData.pMessageString());

		return VK_FALSE;
	}

	private void createSurface() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			LongBuffer surfaceBuffer = stack.mallocLong(1);
			if (glfwCreateWindowSurface(instance, window, null, surfaceBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Unable to create the surface");
			}

			surface = surfaceBuffer.get(0);
		}
	}

	private void pickPhysicalDevice() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer physicalDeviceCountBuffer = stack.callocInt(1);

			vkEnumeratePhysicalDevices(instance, physicalDeviceCountBuffer, null);

			int physicalDeviceCount = physicalDeviceCountBuffer.get(0);

			if (physicalDeviceCount == 0) {
				throw new RuntimeException("No physical device found that supports vulkan");
			}

			PointerBuffer physicalDeviceBuffer = stack.mallocPointer(physicalDeviceCount);

			vkEnumeratePhysicalDevices(instance, physicalDeviceCountBuffer, physicalDeviceBuffer);

			System.out.println(physicalDeviceCount + " physical devices:");
			for (int i = 0; i < physicalDeviceCount; i++) {
				VkPhysicalDevice dev = new VkPhysicalDevice(physicalDeviceBuffer.get(i), instance);
				printPhysicalDevice(dev);
				if (checkPhysicalDeviceCompatibility(dev) && physicalDevice == null) {
					physicalDevice = dev;
				}
			}

			if (physicalDevice == null) {
				throw new RuntimeException("No compatible physical device detected");
			}
		}
	}

	private void printPhysicalDevice(VkPhysicalDevice physicalDevice) {
		MemoryStack stack = MemoryStack.stackGet();

		VkPhysicalDeviceProperties physicalDeviceProperties = VkPhysicalDeviceProperties.mallocStack(stack);

		vkGetPhysicalDeviceProperties(physicalDevice, physicalDeviceProperties);

		int apiVersion = physicalDeviceProperties.apiVersion();
		int driverVersion = physicalDeviceProperties.driverVersion();
		System.out.println("\t" + physicalDeviceProperties.deviceNameString() + " - API: "
				+ VK_VERSION_MAJOR(apiVersion) + "." + VK_VERSION_MINOR(apiVersion) + "." + VK_VERSION_PATCH(apiVersion)
				+ " & DRIVER: " + VK_VERSION_MAJOR(driverVersion) + "." + VK_VERSION_MINOR(driverVersion) + "."
				+ VK_VERSION_PATCH(driverVersion));

		IntBuffer queueFamilyCountBuffer = stack.callocInt(1);
		vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, null);
		int queueFamilyCount = queueFamilyCountBuffer.get(0);

		VkQueueFamilyProperties.Buffer queueFamilyPropertiesBuffer = VkQueueFamilyProperties.mallocStack(queueFamilyCount, stack);
		vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, queueFamilyPropertiesBuffer);

		System.out.println("\t" + queueFamilyCount + " queue families:");
		for (int i = 0; i < queueFamilyCount; i++) {
			VkQueueFamilyProperties queueFamilyProperties = queueFamilyPropertiesBuffer.get(i);

			List<String> queueFamilyFlags = Lists.newArrayList();
			if ((queueFamilyProperties.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
				queueFamilyFlags.add("VK_QUEUE_GRAPHICS_BIT");
			}
			if ((queueFamilyProperties.queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0) {
				queueFamilyFlags.add("VK_QUEUE_COMPUTE_BIT");
			}
			if ((queueFamilyProperties.queueFlags() & VK_QUEUE_TRANSFER_BIT) != 0) {
				queueFamilyFlags.add("VK_QUEUE_TRANSFER_BIT");
			}
			if ((queueFamilyProperties.queueFlags() & VK_QUEUE_SPARSE_BINDING_BIT) != 0) {
				queueFamilyFlags.add("VK_QUEUE_SPARSE_BINDING_BIT");
			}

			System.out.println("\t\tCount: " + queueFamilyProperties.queueCount() + ", Flags: " + queueFamilyFlags);
		}

		IntBuffer extensionCountBuffer = stack.callocInt(1);
		vkEnumerateDeviceExtensionProperties(physicalDevice, (ByteBuffer) null, extensionCountBuffer, null);

		int extensionCount = extensionCountBuffer.get(0);

		VkExtensionProperties.Buffer availableExtensionsBuffer = VkExtensionProperties.mallocStack(extensionCount, stack);
		vkEnumerateDeviceExtensionProperties(physicalDevice, (ByteBuffer) null, extensionCountBuffer, availableExtensionsBuffer);

		System.out.println("\t" + extensionCount + " device extensions found:");
		for (int i = 0; i < extensionCount; i++) {
			VkExtensionProperties extensionProperties = availableExtensionsBuffer.get(i);
			int specVersion = extensionProperties.specVersion();
			System.out.println("\t\t" + extensionProperties.extensionNameString() + " v"
					+ VK_VERSION_MAJOR(specVersion) + "." + VK_VERSION_MINOR(specVersion) + "." + VK_VERSION_PATCH(specVersion));
		}
	}

	private boolean checkPhysicalDeviceCompatibility(VkPhysicalDevice physicalDevice) {
		QueueFamilyIndices indices = findQueueFamilies(physicalDevice);

		boolean extensionsSupported = checkDeviceExtensionSupport(physicalDevice);

		boolean swapChainAdequate = false;
		if (extensionsSupported) {
			SwapChainSupportDetails details = querySwapChainSupport(physicalDevice);
			swapChainAdequate = !details.getFormats().isEmpty() && !details.getPresentModes().isEmpty();
		}

		return indices.isComplete() && extensionsSupported && swapChainAdequate;
	}

	private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice physicalDevice) {
		MemoryStack stack = MemoryStack.stackGet();

		QueueFamilyIndices indices = new QueueFamilyIndices();

		IntBuffer queueFamilyCountBuffer = stack.callocInt(1);
		vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, null);
		int queueFamilyCount = queueFamilyCountBuffer.get(0);

		VkQueueFamilyProperties.Buffer queueFamilyPropertiesBuffer = VkQueueFamilyProperties.mallocStack(queueFamilyCount, stack);
		vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, queueFamilyPropertiesBuffer);

		IntBuffer presentSupportBuffer = stack.callocInt(1);
		for (int i = 0; i < queueFamilyCount; i++) {
			VkQueueFamilyProperties queueFamilyProperties = queueFamilyPropertiesBuffer.get(i);
			if (queueFamilyProperties.queueCount() > 0 && (queueFamilyProperties.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
				indices.setGraphicsFamily(i);
			}

			vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, presentSupportBuffer);
			if (queueFamilyProperties.queueCount() > 0 && presentSupportBuffer.get(0) != 0) {
				indices.setPresentFamily(i);
			}

			if (indices.isComplete()) {
				break;
			}
		}

		return indices;
	}

	private boolean checkDeviceExtensionSupport(VkPhysicalDevice physicalDevice) {
		MemoryStack stack = MemoryStack.stackGet();

		IntBuffer extensionCountBuffer = stack.callocInt(1);
		vkEnumerateDeviceExtensionProperties(physicalDevice, (ByteBuffer) null, extensionCountBuffer, null);

		int extensionCount = extensionCountBuffer.get(0);

		VkExtensionProperties.Buffer availableExtensionsBuffer = VkExtensionProperties.mallocStack(extensionCount, stack);
		vkEnumerateDeviceExtensionProperties(physicalDevice, (ByteBuffer) null, extensionCountBuffer, availableExtensionsBuffer);

		List<String> requiredDeviceExtensions = Lists.newArrayList(DEVICE_EXTENSIONS);

		for (int i = 0; i < extensionCount; i++) {
			requiredDeviceExtensions.remove(availableExtensionsBuffer.get(i).extensionNameString());
		}

		if (!requiredDeviceExtensions.isEmpty()) {
			System.err.println("Missing device extensions: " + requiredDeviceExtensions);
		}

		return requiredDeviceExtensions.isEmpty();
	}

	private SwapChainSupportDetails querySwapChainSupport(VkPhysicalDevice physicalDevice) {
		MemoryStack stack = MemoryStack.stackGet();

		SwapChainSupportDetails details = new SwapChainSupportDetails();

		VkSurfaceCapabilitiesKHR surfaceCapabilitiesKHR = VkSurfaceCapabilitiesKHR.mallocStack(stack);
		vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, surfaceCapabilitiesKHR);
		details.setCapabilities(surfaceCapabilitiesKHR);

		IntBuffer formatCountBuffer = stack.callocInt(1);
		vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCountBuffer, null);

		int formatCount = formatCountBuffer.get(0);

		VkSurfaceFormatKHR.Buffer formatsBuffer = VkSurfaceFormatKHR.mallocStack(formatCount, stack);
		vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCountBuffer, formatsBuffer);

		List<VkSurfaceFormatKHR> formats = Lists.newArrayList();
		for (int i = 0; i < formatCount; i++) {
			formats.add(formatsBuffer.get(i));
		}

		details.setFormats(formats);

		IntBuffer presentModeCountBuffer = stack.callocInt(1);
		vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCountBuffer, null);

		int presentModeCount = presentModeCountBuffer.get(0);

		IntBuffer presentModesBuffer = stack.mallocInt(presentModeCount);
		vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCountBuffer, presentModesBuffer);

		List<Integer> presentModes = Lists.newArrayList();
		for (int i = 0; i < presentModeCount; i++) {
			presentModes.add(presentModesBuffer.get(i));
		}

		details.setPresentModes(presentModes);

		return details;
	}

	private void createLogicalDevice() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			QueueFamilyIndices indices = findQueueFamilies(physicalDevice);

			Set<Integer> uniqueQueueFamilies = ImmutableSet.of(indices.getGraphicsFamily(), indices.getPresentFamily());

			FloatBuffer queuePrioritiesBuffer = stack.floats(1.0f);
			VkDeviceQueueCreateInfo.Buffer queueCreateInfoBuffer = VkDeviceQueueCreateInfo.mallocStack(uniqueQueueFamilies.size(), stack);
			for (int queueFamily : uniqueQueueFamilies) {
				VkDeviceQueueCreateInfo queueCreateInfo = VkDeviceQueueCreateInfo.callocStack(stack);
				queueCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
				queueCreateInfo.queueFamilyIndex(queueFamily);
				queueCreateInfo.pQueuePriorities(queuePrioritiesBuffer);
				queueCreateInfoBuffer.put(queueCreateInfo);
			}
			queueCreateInfoBuffer.flip();

			VkPhysicalDeviceFeatures physicalDeviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack);

			VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.callocStack(stack);
			deviceCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
			deviceCreateInfo.pQueueCreateInfos(queueCreateInfoBuffer);
			deviceCreateInfo.pEnabledFeatures(physicalDeviceFeatures);

			PointerBuffer extensionsBuffer = stack.mallocPointer(DEVICE_EXTENSIONS.length);
			for (int i = 0; i < LAYERS.length; i++) {
				extensionsBuffer.put(i, stack.ASCII(DEVICE_EXTENSIONS[i]));
			}

			deviceCreateInfo.ppEnabledExtensionNames(extensionsBuffer);

			// shouldn't be necessary with up-to-date drivers, but older drivers need this
			if (DEBUG) {
				PointerBuffer layersBuffer = stack.mallocPointer(LAYERS.length);
				for (int i = 0; i < LAYERS.length; i++) {
					layersBuffer.put(i, stack.ASCII(LAYERS[i]));
				}

				deviceCreateInfo.ppEnabledLayerNames(layersBuffer);
			}

			PointerBuffer deviceBuffer = stack.mallocPointer(1);
			if (vkCreateDevice(physicalDevice, deviceCreateInfo, null, deviceBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create the logical device");
			}

			device = new VkDevice(deviceBuffer.get(0), physicalDevice, deviceCreateInfo);

			PointerBuffer queueBuffer = stack.mallocPointer(1);

			vkGetDeviceQueue(device, indices.getGraphicsFamily(), 0, queueBuffer);
			graphicsQueue = new VkQueue(queueBuffer.get(0), device);

			vkGetDeviceQueue(device, indices.getPresentFamily(), 0, queueBuffer);
			presentQueue = new VkQueue(queueBuffer.get(0), device);
		}
	}

	private void createSwapChain() {
		try (MemoryStack stack = MemoryStack.stackPush()) {

			SwapChainSupportDetails swapChainSupportDetails = querySwapChainSupport(physicalDevice);

			VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(swapChainSupportDetails.getFormats());
			int presentMode = chooseSwapPresentMode(swapChainSupportDetails.getPresentModes());
			VkExtent2D extent = chooseSwapExtent(swapChainSupportDetails.getCapabilities());

			int maxImageCount = swapChainSupportDetails.getCapabilities().maxImageCount();
			int imageCount = swapChainSupportDetails.getCapabilities().minImageCount() + 1;
			if (maxImageCount > 0 && imageCount > maxImageCount) {
				imageCount = maxImageCount;
			}

			VkSwapchainCreateInfoKHR swapchainCreateInfo = VkSwapchainCreateInfoKHR.callocStack(stack);
			swapchainCreateInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
			swapchainCreateInfo.surface(surface);
			swapchainCreateInfo.minImageCount(imageCount);
			swapchainCreateInfo.imageFormat(surfaceFormat.format());
			swapchainCreateInfo.imageColorSpace(surfaceFormat.colorSpace());
			swapchainCreateInfo.imageExtent(extent);
			swapchainCreateInfo.imageArrayLayers(1);
			swapchainCreateInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

			QueueFamilyIndices indices = findQueueFamilies(physicalDevice);
			if (indices.getPresentFamily() != indices.getGraphicsFamily()) {
				swapchainCreateInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
				swapchainCreateInfo.pQueueFamilyIndices(stack.ints(indices.getGraphicsFamily(), indices.getPresentFamily()));
			} else {
				swapchainCreateInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
			}

			swapchainCreateInfo.preTransform(swapChainSupportDetails.getCapabilities().currentTransform());
			swapchainCreateInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
			swapchainCreateInfo.presentMode(presentMode);
			swapchainCreateInfo.clipped(true);
			swapchainCreateInfo.oldSwapchain(VK_NULL_HANDLE);

			LongBuffer swapChainBuffer = stack.mallocLong(1);
			if (vkCreateSwapchainKHR(device, swapchainCreateInfo, null, swapChainBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create the swap chain");
			}

			swapChain = swapChainBuffer.get(0);

			IntBuffer imageCountBuffer = stack.mallocInt(1);
			vkGetSwapchainImagesKHR(device, swapChain, imageCountBuffer, null);
			imageCount = imageCountBuffer.get(0);
			LongBuffer swapChainImagesBuffer = stack.mallocLong(imageCount);
			vkGetSwapchainImagesKHR(device, swapChain, imageCountBuffer, swapChainImagesBuffer);

			for (int i = 0; i < imageCount; i++) {
				swapChainImages.add(swapChainImagesBuffer.get(i));
			}

			swapChainImageFormat = surfaceFormat.format();
			swapChainExtent.set(extent);
		}
	}

	private VkSurfaceFormatKHR chooseSwapSurfaceFormat(List<VkSurfaceFormatKHR> availableFormats) {
		if (availableFormats.size() == 1 && availableFormats.get(0).format() == VK_FORMAT_UNDEFINED) {
			return new VkSurfaceFormatKHR(
					MemoryStack.stackGet().malloc(VkSurfaceFormatKHR.SIZEOF)
							.putInt(VkSurfaceFormatKHR.FORMAT, VK_FORMAT_B8G8R8A8_UNORM)
							.putInt(VkSurfaceFormatKHR.COLORSPACE, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
			);
		}

		for (VkSurfaceFormatKHR format : availableFormats) {
			if (format.format() == VK_FORMAT_B8G8R8A8_UNORM && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
				return format;
			}
		}

		return availableFormats.get(0);
	}

	private int chooseSwapPresentMode(List<Integer> availablePresentModes) {
		int bestPresentMode = VK_PRESENT_MODE_FIFO_KHR;

		for (int presentMode : availablePresentModes) {
			if (presentMode == VK_PRESENT_MODE_MAILBOX_KHR) {
				return presentMode;
			} else if (presentMode == VK_PRESENT_MODE_IMMEDIATE_KHR) {
				bestPresentMode = presentMode;
			}
		}

		return bestPresentMode;
	}

	private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities) {
		if (capabilities.currentExtent().width() == 0xFFFFFFFF) {
			MemoryStack stack = MemoryStack.stackGet();

			int minWidth = capabilities.minImageExtent().width();
			int maxWidth = capabilities.maxImageExtent().width();
			int minHeight = capabilities.minImageExtent().height();
			int maxHeight = capabilities.maxImageExtent().height();

			int width = WINDOW_WIDTH;
			int height = WINDOW_HEIGHT;

			if (width < minWidth) {
				width = minWidth;
			} else if (width > maxWidth) {
				width = maxWidth;
			}

			if (height < minHeight) {
				height = minHeight;
			} else if (width > maxHeight) {
				height = maxHeight;
			}

			VkExtent2D extent = VkExtent2D.callocStack(stack);
			extent.set(width, height);

			return extent;
		} else {
			return capabilities.currentExtent();
		}
	}

	private void createImageViews() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			LongBuffer swapChainImageViewBuffer = stack.mallocLong(1);

			for (Long swapChainImage : swapChainImages) {
				VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.callocStack(stack);
				createInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
				createInfo.image(swapChainImage);
				createInfo.viewType(VK_IMAGE_VIEW_TYPE_2D);
				createInfo.format(swapChainImageFormat);
				createInfo.components().r(VK_COMPONENT_SWIZZLE_IDENTITY);
				createInfo.components().g(VK_COMPONENT_SWIZZLE_IDENTITY);
				createInfo.components().b(VK_COMPONENT_SWIZZLE_IDENTITY);
				createInfo.components().a(VK_COMPONENT_SWIZZLE_IDENTITY);
				createInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
				createInfo.subresourceRange().baseMipLevel(0);
				createInfo.subresourceRange().levelCount(1);
				createInfo.subresourceRange().baseArrayLayer(0);
				createInfo.subresourceRange().layerCount(1);

				if (vkCreateImageView(device, createInfo, null, swapChainImageViewBuffer) != VK_SUCCESS) {
					throw new RuntimeException("Failed to create an image view");
				}

				swapChainImageViews.add(swapChainImageViewBuffer.get(0));
			}
		}
	}

	private void createRenderPass() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkAttachmentDescription.Buffer colorAttachmentDescriptionBuffer = VkAttachmentDescription.callocStack(1, stack);
			colorAttachmentDescriptionBuffer.position(0);
			colorAttachmentDescriptionBuffer.format(swapChainImageFormat);
			colorAttachmentDescriptionBuffer.samples(VK_SAMPLE_COUNT_1_BIT);
			colorAttachmentDescriptionBuffer.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
			colorAttachmentDescriptionBuffer.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
			colorAttachmentDescriptionBuffer.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
			colorAttachmentDescriptionBuffer.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
			colorAttachmentDescriptionBuffer.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
			colorAttachmentDescriptionBuffer.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

			VkAttachmentReference.Buffer colorAttachmentReferenceBuffer = VkAttachmentReference.callocStack(1, stack);
			colorAttachmentReferenceBuffer.position(0);
			colorAttachmentReferenceBuffer.attachment(0);
			colorAttachmentReferenceBuffer.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

			VkSubpassDescription.Buffer subpassDescriptionBuffer = VkSubpassDescription.callocStack(1, stack);
			subpassDescriptionBuffer.position(0);
			subpassDescriptionBuffer.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
			subpassDescriptionBuffer.colorAttachmentCount(1);
			subpassDescriptionBuffer.pColorAttachments(colorAttachmentReferenceBuffer);

			VkSubpassDependency.Buffer subpassDependencyBuffer = VkSubpassDependency.callocStack(1, stack);
			subpassDependencyBuffer.position(0);
			subpassDependencyBuffer.srcSubpass(VK_SUBPASS_EXTERNAL);
			subpassDependencyBuffer.dstSubpass(0);
			subpassDependencyBuffer.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
			subpassDependencyBuffer.srcAccessMask(0);
			subpassDependencyBuffer.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
			subpassDependencyBuffer.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

			VkRenderPassCreateInfo renderPassCreateInfo = VkRenderPassCreateInfo.callocStack(stack);
			renderPassCreateInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
			renderPassCreateInfo.pAttachments(colorAttachmentDescriptionBuffer);
			renderPassCreateInfo.pSubpasses(subpassDescriptionBuffer);
			renderPassCreateInfo.pDependencies(subpassDependencyBuffer);

			LongBuffer renderPassBuffer = stack.mallocLong(1);
			if (vkCreateRenderPass(device, renderPassCreateInfo, null, renderPassBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create render pass");
			}

			renderPass = renderPassBuffer.get(0);
		}
	}

	private void createGraphicsPipeline() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer vertShaderCode;
			try {
				vertShaderCode = readFile("simplevert.spv");
			} catch (IOException e) {
				throw new RuntimeException("Failed to load the vertex shader", e);
			}

			ByteBuffer fragShaderCode;
			try {
				fragShaderCode = readFile("simplefrag.spv");
			} catch (IOException e) {
				throw new RuntimeException("Failed to load the fragment shader", e);
			}

			long vertShaderModule = createShaderModule(vertShaderCode);
			long fragShaderModule = createShaderModule(fragShaderCode);

			VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, stack);

			shaderStages.position(0);
			shaderStages.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
			shaderStages.stage(VK_SHADER_STAGE_VERTEX_BIT);
			shaderStages.module(vertShaderModule);
			shaderStages.pName(stack.ASCII("main"));

			shaderStages.position(1);
			shaderStages.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
			shaderStages.stage(VK_SHADER_STAGE_FRAGMENT_BIT);
			shaderStages.module(fragShaderModule);
			shaderStages.pName(stack.ASCII("main"));

			shaderStages.rewind();

			VkPipelineVertexInputStateCreateInfo vertexInputCreateInfo = VkPipelineVertexInputStateCreateInfo.callocStack(stack);
			vertexInputCreateInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
			vertexInputCreateInfo.pVertexBindingDescriptions(null);
			vertexInputCreateInfo.pVertexAttributeDescriptions(null);

			VkPipelineInputAssemblyStateCreateInfo inputAssemblyCreateInfo = VkPipelineInputAssemblyStateCreateInfo.callocStack(stack);
			inputAssemblyCreateInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
			inputAssemblyCreateInfo.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
			inputAssemblyCreateInfo.primitiveRestartEnable(false);

			VkViewport.Buffer viewportBuffer = VkViewport.callocStack(1, stack);
			viewportBuffer.position(0);
			viewportBuffer.x(0);
			viewportBuffer.y(0);
			viewportBuffer.width(swapChainExtent.width());
			viewportBuffer.height(swapChainExtent.height());
			viewportBuffer.minDepth(0);
			viewportBuffer.maxDepth(1);

			VkRect2D.Buffer scissorBuffer = VkRect2D.callocStack(1, stack);
			scissorBuffer.position(0);
			scissorBuffer.offset(VkOffset2D.callocStack(stack).set(0, 0));
			scissorBuffer.extent(swapChainExtent);

			VkPipelineViewportStateCreateInfo viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.callocStack(stack);
			viewportStateCreateInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
			viewportStateCreateInfo.viewportCount(1);
			viewportStateCreateInfo.pViewports(viewportBuffer);
			viewportStateCreateInfo.scissorCount(1);
			viewportStateCreateInfo.pScissors(scissorBuffer);

			VkPipelineRasterizationStateCreateInfo rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.callocStack(stack);
			rasterizationStateCreateInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
			rasterizationStateCreateInfo.depthClampEnable(false);
			rasterizationStateCreateInfo.rasterizerDiscardEnable(false);
			rasterizationStateCreateInfo.polygonMode(VK_POLYGON_MODE_FILL);
			rasterizationStateCreateInfo.lineWidth(1.0f);
			rasterizationStateCreateInfo.cullMode(VK_CULL_MODE_BACK_BIT);
			rasterizationStateCreateInfo.frontFace(VK_FRONT_FACE_CLOCKWISE);
			rasterizationStateCreateInfo.depthBiasEnable(false);
			rasterizationStateCreateInfo.depthBiasConstantFactor(0.0f);
			rasterizationStateCreateInfo.depthBiasClamp(0.0f);
			rasterizationStateCreateInfo.depthBiasSlopeFactor(0.0f);

			VkPipelineMultisampleStateCreateInfo multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.callocStack(stack);
			multisampleStateCreateInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
			multisampleStateCreateInfo.sampleShadingEnable(false);
			multisampleStateCreateInfo.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
			multisampleStateCreateInfo.minSampleShading(1.0f);
			multisampleStateCreateInfo.pSampleMask(null);
			multisampleStateCreateInfo.alphaToCoverageEnable(false);
			multisampleStateCreateInfo.alphaToOneEnable(false);

			VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachmentStateBuffer = VkPipelineColorBlendAttachmentState.callocStack(1, stack);
			colorBlendAttachmentStateBuffer.position(0);
			colorBlendAttachmentStateBuffer.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
					| VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
			colorBlendAttachmentStateBuffer.blendEnable(false);
			colorBlendAttachmentStateBuffer.srcColorBlendFactor(VK_BLEND_FACTOR_ONE);
			colorBlendAttachmentStateBuffer.dstColorBlendFactor(VK_BLEND_FACTOR_ZERO);
			colorBlendAttachmentStateBuffer.colorBlendOp(VK_BLEND_OP_ADD);
			colorBlendAttachmentStateBuffer.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE);
			colorBlendAttachmentStateBuffer.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO);
			colorBlendAttachmentStateBuffer.alphaBlendOp(VK_BLEND_OP_ADD);

			VkPipelineColorBlendStateCreateInfo colorBlendStateCreateInfo = VkPipelineColorBlendStateCreateInfo.callocStack(stack);
			colorBlendStateCreateInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
			colorBlendStateCreateInfo.logicOpEnable(false);
			colorBlendStateCreateInfo.logicOp(VK_LOGIC_OP_COPY);
			colorBlendStateCreateInfo.pAttachments(colorBlendAttachmentStateBuffer);
			colorBlendStateCreateInfo.blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

			VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.callocStack(stack);
			pipelineLayoutCreateInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
			pipelineLayoutCreateInfo.pSetLayouts(null);
			pipelineLayoutCreateInfo.pPushConstantRanges(null);

			LongBuffer pipelineLayoutBuffer = stack.mallocLong(1);
			if (vkCreatePipelineLayout(device, pipelineLayoutCreateInfo, null, pipelineLayoutBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create pipeline layout");
			}

			pipelineLayout = pipelineLayoutBuffer.get(0);

			VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfoBuffer = VkGraphicsPipelineCreateInfo.callocStack(1, stack);
			pipelineCreateInfoBuffer.position(0);
			pipelineCreateInfoBuffer.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
			pipelineCreateInfoBuffer.pStages(shaderStages);
			pipelineCreateInfoBuffer.pVertexInputState(vertexInputCreateInfo);
			pipelineCreateInfoBuffer.pInputAssemblyState(inputAssemblyCreateInfo);
			pipelineCreateInfoBuffer.pViewportState(viewportStateCreateInfo);
			pipelineCreateInfoBuffer.pRasterizationState(rasterizationStateCreateInfo);
			pipelineCreateInfoBuffer.pMultisampleState(multisampleStateCreateInfo);
			pipelineCreateInfoBuffer.pDepthStencilState(null);
			pipelineCreateInfoBuffer.pColorBlendState(colorBlendStateCreateInfo);
			pipelineCreateInfoBuffer.pDynamicState(null);
			pipelineCreateInfoBuffer.layout(pipelineLayout);
			pipelineCreateInfoBuffer.renderPass(renderPass);
			pipelineCreateInfoBuffer.subpass(0);
			pipelineCreateInfoBuffer.basePipelineHandle(VK_NULL_HANDLE);
			pipelineCreateInfoBuffer.basePipelineIndex(-1);

			LongBuffer graphicsPipelineBuffer = stack.mallocLong(1);
			if (vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineCreateInfoBuffer, null, graphicsPipelineBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create graphics pipeline");
			}

			graphicsPipeline = graphicsPipelineBuffer.get(0);

			vkDestroyShaderModule(device, vertShaderModule, null);
			vkDestroyShaderModule(device, fragShaderModule, null);
		}
	}

	private ByteBuffer readFile(String filename) throws IOException {
		return BufferUtils.toByteBuffer(Channels.newChannel(getClass().getResourceAsStream(filename)));
	}

	private long createShaderModule(ByteBuffer code) {
		MemoryStack stack = MemoryStack.stackGet();

		VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.callocStack(stack);
		createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
		createInfo.pCode(code);

		LongBuffer shaderModuleBuffer = stack.mallocLong(1);
		if (vkCreateShaderModule(device, createInfo, null, shaderModuleBuffer) != VK_SUCCESS) {
			throw new RuntimeException("Failed to create shader module");
		}

		memFree(code);

		return shaderModuleBuffer.get(0);
	}

	private void createFramebuffers() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			LongBuffer framebufferBuffer = stack.mallocLong(1);
			LongBuffer attachments = stack.mallocLong(1);

			for (long swapChainImageView : swapChainImageViews) {
				attachments.put(0, swapChainImageView);

				VkFramebufferCreateInfo framebufferCreateInfo = VkFramebufferCreateInfo.callocStack(stack);
				framebufferCreateInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
				framebufferCreateInfo.renderPass(renderPass);
				framebufferCreateInfo.pAttachments(attachments);
				framebufferCreateInfo.width(swapChainExtent.width());
				framebufferCreateInfo.height(swapChainExtent.height());
				framebufferCreateInfo.layers(1);

				if (vkCreateFramebuffer(device, framebufferCreateInfo, null, framebufferBuffer) != VK_SUCCESS) {
					throw new RuntimeException("Failed to create a framebuffer");
				}

				swapChainFramebuffers.add(framebufferBuffer.get(0));
			}
		}
	}

	private void createCommandPool() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			QueueFamilyIndices queueFamilyIndices = findQueueFamilies(physicalDevice);

			VkCommandPoolCreateInfo commandPoolCreateInfo = VkCommandPoolCreateInfo.callocStack(stack);
			commandPoolCreateInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
			commandPoolCreateInfo.queueFamilyIndex(queueFamilyIndices.getGraphicsFamily());
			commandPoolCreateInfo.flags(0);

			LongBuffer commandPoolBuffer = stack.mallocLong(1);
			if (vkCreateCommandPool(device, commandPoolCreateInfo, null, commandPoolBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create command pool");
			}

			commandPool = commandPoolBuffer.get(0);
		}
	}

	private void createCommandBuffers() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			int commandBufferCount = swapChainFramebuffers.size();

			VkCommandBufferAllocateInfo commandBufferAllocateInfo = VkCommandBufferAllocateInfo.callocStack(stack);
			commandBufferAllocateInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
			commandBufferAllocateInfo.commandPool(commandPool);
			commandBufferAllocateInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
			commandBufferAllocateInfo.commandBufferCount(commandBufferCount);

			PointerBuffer commandBufferBuffer = stack.mallocPointer(commandBufferCount);
			if (vkAllocateCommandBuffers(device, commandBufferAllocateInfo, commandBufferBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to allocate command buffers");
			}

			VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
			beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
			beginInfo.flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT);

			VkClearColorValue clearColor = VkClearColorValue.callocStack(stack).float32(stack.floats(0.0f, 0.0f, 0.0f, 1.0f));
			VkClearValue.Buffer clearValueBuffer = VkClearValue.callocStack(1, stack).color(clearColor);

			for (int i = 0; i < commandBufferCount; i++) {
				VkCommandBuffer commandBuffer = new VkCommandBuffer(commandBufferBuffer.get(i), device);
				commandBuffers.add(commandBuffer);

				if (vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS) {
					throw new RuntimeException("Failed to begin recording to a command buffer");
				}

				VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.callocStack(stack);
				renderPassBeginInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
				renderPassBeginInfo.renderPass(renderPass);
				renderPassBeginInfo.framebuffer(swapChainFramebuffers.get(i));
				renderPassBeginInfo.renderArea().offset(VkOffset2D.callocStack(stack).set(0, 0));
				renderPassBeginInfo.renderArea().extent(swapChainExtent);
				renderPassBeginInfo.pClearValues(clearValueBuffer);

				vkCmdBeginRenderPass(commandBuffer, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

				vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);

				vkCmdDraw(commandBuffer, 3, 1, 0, 0);

				vkCmdEndRenderPass(commandBuffer);

				if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
					throw new RuntimeException("Failed to record a command buffer");
				}
			}
		}
	}

	private void createSyncObjects() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.callocStack(stack);
			semaphoreCreateInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

			VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.callocStack(stack);
			fenceCreateInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
			fenceCreateInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

			LongBuffer syncObjectBuffer = stack.mallocLong(3);

			for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
				if (vkCreateSemaphore(device, semaphoreCreateInfo, null, syncObjectBuffer.position(0)) != VK_SUCCESS
						|| vkCreateSemaphore(device, semaphoreCreateInfo, null, syncObjectBuffer.position(1)) != VK_SUCCESS
						|| vkCreateFence(device, fenceCreateInfo, null, syncObjectBuffer.position(2)) != VK_SUCCESS) {
					throw new RuntimeException("Failed to create sync objects for a frame");
				}

				imageAvailableSemaphores[i] = syncObjectBuffer.get(0);
				renderFinishedSemaphores[i] = syncObjectBuffer.get(1);
				inFlightFences[i] = syncObjectBuffer.get(2);
			}
		}
	}

	private void mainLoop() {
		while (!glfwWindowShouldClose(window)) {
			glfwPollEvents();

			drawFrame();
		}

		vkDeviceWaitIdle(device);
	}

	private void drawFrame() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			vkWaitForFences(device, inFlightFences[currentFrame], true, -1L);
			vkResetFences(device, inFlightFences[currentFrame]);

			IntBuffer imageIndexBuffer = stack.mallocInt(1);
			vkAcquireNextImageKHR(device, swapChain, -1, imageAvailableSemaphores[currentFrame], VK_NULL_HANDLE, imageIndexBuffer);
			int imageIndex = imageIndexBuffer.get(0);

			VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
			submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
			submitInfo.waitSemaphoreCount(1);
			submitInfo.pWaitSemaphores(stack.longs(imageAvailableSemaphores[currentFrame]));
			submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
			submitInfo.pCommandBuffers(stack.pointers(commandBuffers.get(imageIndex)));
			submitInfo.pSignalSemaphores(stack.longs(renderFinishedSemaphores[currentFrame]));

			if (vkQueueSubmit(graphicsQueue, submitInfo, inFlightFences[currentFrame]) != VK_SUCCESS) {
				throw new RuntimeException("Failed to submit draw command buffer");
			}

			VkPresentInfoKHR presentInfo = VkPresentInfoKHR.callocStack(stack);
			presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
			presentInfo.pWaitSemaphores(stack.longs(renderFinishedSemaphores[currentFrame]));
			presentInfo.swapchainCount(1);
			presentInfo.pSwapchains(stack.longs(swapChain));
			presentInfo.pImageIndices(imageIndexBuffer);
			presentInfo.pResults(null);

			vkQueuePresentKHR(presentQueue, presentInfo);

			currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
		}
	}

	private void cleanup() {
		for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
			vkDestroySemaphore(device, imageAvailableSemaphores[i], null);
			vkDestroySemaphore(device, renderFinishedSemaphores[i], null);
			vkDestroyFence(device, inFlightFences[i], null);
		}

		vkDestroyCommandPool(device, commandPool, null);

		for (long framebuffer : swapChainFramebuffers) {
			vkDestroyFramebuffer(device, framebuffer, null);
		}

		vkDestroyPipeline(device, graphicsPipeline, null);
		vkDestroyPipelineLayout(device, pipelineLayout, null);
		vkDestroyRenderPass(device, renderPass, null);

		for (long imageView : swapChainImageViews) {
			vkDestroyImageView(device, imageView, null);
		}

		vkDestroySwapchainKHR(device, swapChain, null);
		vkDestroyDevice(device, null);
		vkDestroySurfaceKHR(instance, surface, null);

		if (DEBUG) {
			vkDestroyDebugUtilsMessengerEXT(instance, debugUtilsMessenger, null);
		}

		vkDestroyInstance(instance, null);

		glfwDestroyWindow(window);
		glfwTerminate();
	}

	private static class QueueFamilyIndices {
		private int graphicsFamily;
		private boolean hasGraphicsFamily = false;
		private int presentFamily;
		private boolean hasPresentFamily = false;

		public void setGraphicsFamily(int graphicsFamily) {
			this.graphicsFamily = graphicsFamily;
			hasGraphicsFamily = true;
		}

		public void setPresentFamily(int presentFamily) {
			this.presentFamily = presentFamily;
			hasPresentFamily = true;
		}

		public int getGraphicsFamily() {
			return graphicsFamily;
		}

		public int getPresentFamily() {
			return presentFamily;
		}

		public boolean isComplete() {
			return hasGraphicsFamily && hasPresentFamily;
		}
	}

	private static class SwapChainSupportDetails {
		private VkSurfaceCapabilitiesKHR capabilities;
		private List<VkSurfaceFormatKHR> formats;
		private List<Integer> presentModes;

		public VkSurfaceCapabilitiesKHR getCapabilities() {
			return capabilities;
		}

		public void setCapabilities(VkSurfaceCapabilitiesKHR capabilities) {
			this.capabilities = capabilities;
		}

		public List<VkSurfaceFormatKHR> getFormats() {
			return formats;
		}

		public void setFormats(List<VkSurfaceFormatKHR> formats) {
			this.formats = formats;
		}

		public List<Integer> getPresentModes() {
			return presentModes;
		}

		public void setPresentModes(List<Integer> presentModes) {
			this.presentModes = presentModes;
		}
	}
}
