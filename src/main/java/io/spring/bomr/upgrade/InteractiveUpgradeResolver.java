/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.bomr.upgrade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import io.spring.bomr.upgrade.BomVersions.BomVersion;
import org.eclipse.aether.version.Version;
import org.springframework.util.StringUtils;

/**
 * Interactive {@link UpgradeResolver} that uses command line input to choose the upgrades
 * to apply.
 *
 * @author Andy Wilkinson
 */
final class InteractiveUpgradeResolver implements UpgradeResolver {

	private final VersionResolver versionResolver;

	InteractiveUpgradeResolver(VersionResolver versionResolver) {
		this.versionResolver = versionResolver;
	}

	@Override
	public List<Upgrade> resolveUpgrades(Collection<Project> projects) {
		return projects.stream().map(this::resolveUpgrade)
				.filter((upgrade) -> upgrade != null).collect(Collectors.toList());
	}

	private Upgrade resolveUpgrade(Project project) {
		Map<String, SortedSet<Version>> moduleVersions = new LinkedHashMap<>();
		project.getModules()
				.forEach((module) -> moduleVersions.put(module.getArtifactId(),
						getLaterVersionsForModule(module, project.getVersion())));
		List<Version> allVersions = moduleVersions.values().stream()
				.flatMap(SortedSet::stream).distinct().collect(Collectors.toList());
		if (allVersions.isEmpty()) {
			return null;
		}
		System.out.println();
		System.out.println(project.getName() + " " + project.getVersion().getVersion());
		System.out.println();
		forEachWithIndex(allVersions, (index, version) -> {
			List<String> missingModules = getMissingModules(moduleVersions, version);
			System.out.print("    " + (index + 1) + ". " + version);
			if (!missingModules.isEmpty()) {
				System.out.print(" (Some modules are missing: "
						+ StringUtils.collectionToDelimitedString(missingModules, ", ")
						+ ")");
			}
			System.out.println();
		});
		System.out.println();
		String read = System.console().readLine("Please select a version: ");
		if (!StringUtils.hasText(read)) {
			return null;
		}
		int selection = Integer.parseInt(read);
		return new Upgrade(project, allVersions.get(selection - 1));
	}

	private List<String> getMissingModules(Map<String, SortedSet<Version>> moduleVersions,
			Version version) {
		List<String> missingModules = new ArrayList<>();
		moduleVersions.forEach((name, versions) -> {
			if (!versions.contains(version)) {
				missingModules.add(name);
			}
		});
		return missingModules;
	}

	private SortedSet<Version> getLaterVersionsForModule(Module module,
			BomVersion currentVersion) {
		SortedSet<Version> versions = this.versionResolver.resolveVersions(module);
		versions.removeIf(
				(version) -> version.compareTo(currentVersion.getVersion()) <= 0);
		return versions;
	}

	private <T> void forEachWithIndex(Collection<T> collection,
			BiConsumer<Integer, T> consumer) {
		int i = 0;
		for (T item : collection) {
			consumer.accept(i++, item);
		}
	}

}
