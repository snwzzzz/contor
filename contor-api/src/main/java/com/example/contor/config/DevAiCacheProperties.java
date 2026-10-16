package com.example.contor.config;

import com.example.contor.ai.CachingLlmClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Dev profile only ({@code app.ai.cache.*}): replay model answers from disk so restarting or re-analyzing does not wait
 * for the GPU again. See {@link CachingLlmClient}.
 *
 * @param mode      {@code off} (default), {@code on} (answer from the cache, ask the model only on a miss) or
 *                  {@code refresh} (always ask, overwrite the stored answer)
 * @param directory where the answers are stored (plain text about your mail: keep it out of git and backups). A string,
 *                  not a {@link Path}: Spring binds a {@code Path} through the web app's resource loader, which rejects
 *                  {@code ../} (the default)
 */
@ConfigurationProperties("app.ai.cache")
public record DevAiCacheProperties(@DefaultValue("off") String mode, @DefaultValue(CachingLlmClient.DEFAULT_DIRECTORY) String directory) {

    public Path directoryPath() {
        return Path.of(directory);
    }
}
