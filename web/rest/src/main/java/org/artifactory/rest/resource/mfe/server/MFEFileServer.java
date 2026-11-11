package org.artifactory.rest.resource.mfe.server;

import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.exception.http.NotFoundException;

/**
 * Interface for serving MFE files. This interface is used to serve static MFE files.
 */
public interface MFEFileServer {
    /**
     * @param httpResponse The response to the client, assuming this is being used in the context of a servlet
     * @param resourcePath The path of the resource to serve
     * @param contentType Response content type
     * @throws NotFoundException
     */
    void serveFile(HttpServletResponse httpResponse, String resourcePath, String contentType) throws NotFoundException;

    /**
     * Returns the content of a file as a String. This is used to read the content of the file
     *
     * @param resourcePath The path of the resource to read
     * @return String representation of the file content
     * @throws NotFoundException
     */
    String readFileContent(String resourcePath) throws NotFoundException;

    /**
     * Gets the version of a specific MFE by name.
     *
     * @param mfeName The name of the MFE (e.g., "core", "rtui")
     * @return The version of the specified MFE
     * @throws NotFoundException if the MFE name is not found
     */
    String getMfeVersion(String mfeName) throws NotFoundException;
}
