# Publishing

## Prerequisites

- Go to [Maven Central](https://central.sonatype.com/) and create an account, then generate a user token

- Generate a GPG key pair

    ```shell
    gpg --gen-key
    gpg --list-keys
    gpg --armor --export <key-id> --output public.gpg
    gpg --armor --export-secret-key <key-id> --output secret.gpg
    gpg --keyserver keyserver.ubuntu.com --send-keys <key-id>
    ```

- Add secrets to GitHub repository
    
    ```shell
    gh secret set MAVENCENTRAL_USERNAME --body "your-username"
    gh secret set MAVENCENTRAL_PASSWORD --body "your-password"
    gh secret set GPG_PUBLIC_KEY --body < /path/to/public.gpg
    gh secret set GPG_SECRET_KEY --body < /path/to/secret.gpg
    gh secret set GPG_PASSPHRASE --body "your-gpg-passphrase"
    ```

## Publish Snapshot

```shell
export MAVENCENTRAL_USERNAME="your-username"
export MAVENCENTRAL_PASSWORD="your-password"

./gradlew publish jreleaserDeploy -Pversion=0.1.0-SNAPSHOT
```

## Publish

```shell
export MAVENCENTRAL_USERNAME="your-username"
export MAVENCENTRAL_PASSWORD="your-password"
export GPG_PASSPHRASE="your-gpg-passphrase"
export GPG_PUBLIC_KEY="$(cat /path/to/public.gpg)"
export GPG_SECRET_KEY="$(cat /path/to/secret.gpg)"

./gradlew publish jreleaserDeploy -Pversion=0.1.0
```



