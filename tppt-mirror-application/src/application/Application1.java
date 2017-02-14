package application;

import java.util.Arrays;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.p2.internal.repository.tools.MirrorApplication;

public class Application1 implements IApplication {

	@Override
	public Object start(IApplicationContext context) throws Exception {
		System.err.println("Application1.start " + context);
		Object args = context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		System.err.println("Application1.start " + Arrays.asList((String[])args));
		Object x = MirrorApplication.class;
		System.err.println("Application1.start " + x);
		return null;
	}

	@Override
	public void stop() {
		System.err.println("Application1.stop");
	}

}
