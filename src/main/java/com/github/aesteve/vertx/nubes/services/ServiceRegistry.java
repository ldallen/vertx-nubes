package com.github.aesteve.vertx.nubes.services;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.serviceproxy.ProxyHelper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.aesteve.vertx.nubes.annotations.services.Consumer;
import com.github.aesteve.vertx.nubes.annotations.services.PeriodicTask;
import com.github.aesteve.vertx.nubes.annotations.services.Proxify;
import com.github.aesteve.vertx.nubes.annotations.services.ServiceProxy;
import com.github.aesteve.vertx.nubes.utils.async.MultipleFutures;

public class ServiceRegistry {

	private final static Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

	private Map<String, Object> services;
	private Map<String, Object> serviceProxies;
	private Set<Long> timerIds;

	private Vertx vertx;

	public ServiceRegistry(Vertx vertx) {
		this.vertx = vertx;
		services = new HashMap<>();
		serviceProxies = new HashMap<>();
		timerIds = new HashSet<>();
	}

	public void registerService(String name, Object service) {
		services.put(name, service);
	}

	public Object get(String name) {
		return services.get(name);
	}

	public Object get(Field field) {
		com.github.aesteve.vertx.nubes.annotations.services.Service annot = field.getAnnotation(com.github.aesteve.vertx.nubes.annotations.services.Service.class);
		if (annot != null) {
			return get(annot.value());
		}
		ServiceProxy proxyAnnot = field.getAnnotation(ServiceProxy.class);
		if (proxyAnnot == null) {
			return null;
		}
		Class<?> serviceInterface = getInterface(field.getType());
		if (serviceInterface == null) {
			log.error("Could not inject service for : " + field.getName() + " could not find the matching proxified service using @ProxyGen");
			return null;
		}
		String address = proxyAnnot.value();
		if (serviceProxies.get(address) != null) {
			return serviceProxies.get(address);
		}
		else {
			Object service = createEbProxyClass(serviceInterface, address);
			serviceProxies.put(address, service);
			return service;
		}
	}

	public Collection<Object> services() {
		return services.values();
	}

	public boolean isEmpty() {
		return services.isEmpty();
	}

	public void startAll(Future<Void> future) {
		if (isEmpty()) {
			future.complete();
			return;
		}
		MultipleFutures<Void> futures = new MultipleFutures<>(future);
		services().forEach(obj -> {
			introspectService(obj);
			if (obj instanceof Service) {
				Service service = (Service) obj;
				service.init(vertx);
				futures.add(service::start);
			}
		});
		futures.start();
	}

	public void stopAll(Future<Void> future) {
		if (isEmpty()) {
			future.complete();
			return;
		}
		timerIds.forEach(timerId -> {
			vertx.cancelTimer(timerId);
		});
		MultipleFutures<Void> futures = new MultipleFutures<>(future);
		services().forEach(obj -> {
			if (obj instanceof Service) {
				Service service = (Service) obj;
				futures.add(service::stop);
			}
		});
		futures.start();
	}

	private void introspectService(Object service) {
		Class<?> serviceClass = service.getClass();
		Proxify annot = serviceClass.getAnnotation(Proxify.class);
		if (annot != null) {
			createServiceProxy(annot.value(), service);
		}
		for (Method method : service.getClass().getMethods()) {
			PeriodicTask periodicTask = method.getAnnotation(PeriodicTask.class);
			if (periodicTask != null) {
				if (method.getParameterTypes().length > 0) {
					log.error("Periodic tasks should not have parameters");
					return;
				}
				vertx.setPeriodic(periodicTask.value(), timerId -> {
					timerIds.add(timerId);
					try {
						method.invoke(service);
					} catch (Exception e) {
						log.error("Error while running periodic task", e);
					}
				});
			}
			Consumer consumes = method.getAnnotation(Consumer.class);
			if (consumes != null) {
				Class<?>[] parameterTypes = method.getParameterTypes();
				if (parameterTypes.length != 1 || !parameterTypes[0].equals(Message.class)) {
					log.error("Cannot register consumer on method : " + getFullName(service, method));
					log.error("Method should only declare one parameter of io.vertx.core.eventbus.Message type.");
					return;
				}
				vertx.eventBus().consumer(consumes.value(), message -> {
					try {
						method.invoke(service, message);
					} catch (Exception e) {
						log.error("Exception happened during message handling on method : " + getFullName(service, method), e);
					}
				});
			}
		}
	}

	private static String getFullName(Object service, Method method) {
		return service.getClass().getName() + "." + method.getName();
	}

	private <T> void createServiceProxy(String address, T service) {
		Class<T> serviceClass = getInterface(service.getClass());
		if (serviceClass == null) {
			log.error("Could not find a @ProxyGen super interface for class : " + service.getClass().getName() + " cannot proxy it ver the eventBus");
			return;
		}
		ProxyHelper.registerService(serviceClass, vertx, service, address);
	}

	@SuppressWarnings("unchecked")
	public static <T> Class<T> getInterface(Class<?> serviceClass) {
		if (serviceClass.isAnnotationPresent(ProxyGen.class)) {
			return (Class<T>) serviceClass;
		}
		Class<?>[] interfaces = serviceClass.getInterfaces();
		for (Class<?> someInterface : interfaces) {
			if (someInterface.isAnnotationPresent(ProxyGen.class)) { // it must be it
				return (Class<T>) someInterface;
			}
		}
		return null;
	}

	public Object createEbProxyClass(Class<?> serviceInterface, String address) {
		String name = serviceInterface.getName() + "VertxEBProxy";
		try {
			return Class.forName(name).getConstructor(Vertx.class, String.class).newInstance(vertx, address);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
			throw new VertxException("Could not create your service proxy for class : " + serviceInterface, e);
		}
	}
}
