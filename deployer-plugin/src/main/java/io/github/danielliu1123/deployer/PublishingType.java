package io.github.danielliu1123.deployer;

/**
 * Publishing type for uploading deployment bundles to Maven Central.
 *
 * <p> Default is {@link #USER_MANAGED}.
 *
 * @author Freeman
 * @see <a href="https://central.sonatype.org/publish/publish-portal-api/#uploading-a-deployment-bundle>">Upload Bundle</a>
 */
public enum PublishingType {
    AUTOMATIC, USER_MANAGED
}
