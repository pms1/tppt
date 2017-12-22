package com.github.pms1.tppt.mirror;

import java.util.Set;
import java.util.function.BiFunction;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;

public interface SlicingAlgorithm extends BiFunction<Set<IInstallableUnit>, IProgressMonitor, Set<IInstallableUnit>> {

}
