package com.betterfarming;

import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.eventbus.Subscribe;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * RuneLite's EventBus rejects registration when a @Subscribe method is not
 * named on&lt;EventSimpleName&gt; — an IllegalArgumentException at plugin startUp
 * that headless service tests never hit (they call handlers directly). This
 * mirrors the check so the failure shows up at CI time instead of as a
 * plugin that refuses to start in the client.
 */
public class EventBusNamingTest
{
	@Test
	public void allSubscribedMethodsFollowTheEventBusNamingRule() throws IOException
	{
		List<String> violations = new ArrayList<>();
		for (ClassPath.ClassInfo info : ClassPath.from(getClass().getClassLoader())
			.getTopLevelClassesRecursive("com.betterfarming"))
		{
			Class<?> clazz;
			try
			{
				clazz = info.load();
			}
			catch (Throwable t)
			{
				continue;
			}
			for (Method method : clazz.getDeclaredMethods())
			{
				if (method.getAnnotation(Subscribe.class) == null)
				{
					continue;
				}
				Class<?>[] params = method.getParameterTypes();
				String expected = params.length == 1 ? "on" + params[0].getSimpleName() : "(one parameter)";
				if (params.length != 1 || !method.getName().equals(expected))
				{
					violations.add(clazz.getName() + "#" + method.getName()
						+ " should be named " + expected);
				}
			}
		}
		assertTrue(String.join("; ", violations), violations.isEmpty());
	}
}
