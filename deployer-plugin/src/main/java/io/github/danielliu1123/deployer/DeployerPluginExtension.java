package io.github.danielliu1123.deployer;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;

/**
 *
 * @author Freeman
 */
public abstract class DeployerPluginExtension {

    private final ReleaseConfig release;
    private final SnapshotConfig snapshot;

    public DeployerPluginExtension(Project project) {
        var objects = project.getObjects();
        this.release = objects.newInstance(ReleaseConfig.class, project);
        this.snapshot = objects.newInstance(SnapshotConfig.class);
    }

    public ReleaseConfig getRelease() {
        return release;
    }

    public void release(Action<? super ReleaseConfig> action) {
        action.execute(release);
    }

    public SnapshotConfig getSnapshot() {
        return snapshot;
    }

    public void snapshot(Action<? super SnapshotConfig> action) {
        action.execute(snapshot);
    }

    // ------------------------------------------------------------------------
    // Inner config classes
    // ------------------------------------------------------------------------

    /**
     * release { ... }
     */
    public static abstract class ReleaseConfig {

        public abstract DirectoryProperty getDir();

        public abstract Property<String> getUsername();

        public abstract Property<String> getPassword();

        private final SignConfig sign;

        @Inject
        public ReleaseConfig(Project project) {
            var objects = project.getObjects();
            this.sign = objects.newInstance(SignConfig.class);
        }

        public SignConfig getSign() {
            return sign;
        }

        public void sign(Action<? super SignConfig> action) {
            action.execute(sign);
        }
    }

    /**
     * snapshot { ... }
     */
    public static abstract class SnapshotConfig {

        public abstract DirectoryProperty getDir();

        public abstract Property<String> getUsername();

        public abstract Property<String> getPassword();
    }

    /**
     * sign { ... }
     */
    public static abstract class SignConfig {

        public abstract Property<String> getSecretKey();

        public abstract Property<String> getPublicKey();

        public abstract Property<String> getPassphrase();
    }
}