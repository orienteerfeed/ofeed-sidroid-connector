package com.orienteerfeed.ofeed_sidroid_connector;

import java.util.Locale;

/**
 * Translation of HTTP status code to the corresponding meaning in plain text. Includes some unofficial codes from Wikipedia.
 */
class HttpStatusCodes {

    /**
     * Helper class for storing a status code and its corresponding meaning in plain text.
     */
    private static class HttpStatusCode {
        private final int code;
        private final String meaning;

        HttpStatusCode(int code, String meaning) {
            this.code = code;
            this.meaning = meaning;
        }
    }

    /**
     * Translation of an HTTP status code to to the corresponding meaning in plain text.
     *
     * @param code The status code to be translated.
     * @return The meaning for the given code formatted as "code (meaning).", or, "code." if the given code could not be found.
     */
    static String getMeaning(int code) {
        for (HttpStatusCode httpStatusCode : HTTP_STATUS_CODES) {
            if (httpStatusCode.code == code) {
                return String.format(Locale.US, "%d (%s).", code, httpStatusCode.meaning);
            }
        }
        return String.format(Locale.US, "%d.", code);
    }

    /**
     * Array of status codes and the corresponding meanings in plain text.
     */
    private static final HttpStatusCode[] HTTP_STATUS_CODES = {
            // Information.
            new HttpStatusCode(100, "Continue"),
            new HttpStatusCode(101, "Switching Protocols"),
            new HttpStatusCode(102, "Processing (WebDAV, deprecated)"),
            new HttpStatusCode(103, "Early Hints"),

            // Successful.
            new HttpStatusCode(200, "OK"),
            new HttpStatusCode(201, "Created"),
            new HttpStatusCode(202, "Accepted"),
            new HttpStatusCode(203, "Non-Authoritative Information"),
            new HttpStatusCode(204, "No Content"),
            new HttpStatusCode(205, "Reset Content"),
            new HttpStatusCode(206, "Partial Content"),
            new HttpStatusCode(207, "Multi-Status (WebDAV)"),
            new HttpStatusCode(208, "Already Reported (WebDAV)"),

            // Redirection.
            new HttpStatusCode(300, "Multiple Choices"),
            new HttpStatusCode(301, "Moved Permanently"),
            new HttpStatusCode(302, "Found"),
            new HttpStatusCode(303, "See Other"),
            new HttpStatusCode(304, "Not Modified"),
            new HttpStatusCode(305, "Use Proxy"),
            new HttpStatusCode(306, "Switch Proxy (no longer used)"),
            new HttpStatusCode(307, "Temporary Redirect"),
            new HttpStatusCode(308, "Permanent Redirect"),

            // Client error.
            new HttpStatusCode(400, "Bad Request"),
            new HttpStatusCode(401, "Unauthorized"),
            new HttpStatusCode(402, "Payment Required"),
            new HttpStatusCode(403, "Forbidden"),
            new HttpStatusCode(404, "Not Found"),
            new HttpStatusCode(405, "Method Not Allowed"),
            new HttpStatusCode(406, "Not Acceptable"),
            new HttpStatusCode(407, "Proxy Authentication Required"),
            new HttpStatusCode(408, "Request Timeout"),
            new HttpStatusCode(409, "Conflict"),
            new HttpStatusCode(410, "Gone"),
            new HttpStatusCode(411, "Length Required"),
            new HttpStatusCode(412, "Precondition Failed"),
            new HttpStatusCode(413, "Request Too Large"),
            new HttpStatusCode(414, "Request-URI Too Long"),
            new HttpStatusCode(415, "Unsupported Media Type"),
            new HttpStatusCode(416, "Range Not Satisfiable"),
            new HttpStatusCode(417, "Expectation Failed"),
            new HttpStatusCode(418, "I'm a teapot (IETF April Fools' joke)"),
            new HttpStatusCode(421, "Misdirected Request"),
            new HttpStatusCode(422, "Unprocessable Content"),
            new HttpStatusCode(423, "Locked (WebDAV)"),
            new HttpStatusCode(424, "Failed Dependency (WebDAV)"),
            new HttpStatusCode(425, "Too Early"),
            new HttpStatusCode(426, "Upgrade Required"),
            new HttpStatusCode(428, "Precondition Required"),
            new HttpStatusCode(429, "Too Many Requests"),
            new HttpStatusCode(431, "Request Header Fields Too Large"),
            new HttpStatusCode(451, "Unavailable For Legal Reasons"),

            // Server error.
            new HttpStatusCode(500, "Internal Server Error"),
            new HttpStatusCode(501, "Not Implemented"),
            new HttpStatusCode(502, "Bad Gateway"),
            new HttpStatusCode(503, "Service Unavailable"),
            new HttpStatusCode(504, "Gateway Timeout"),
            new HttpStatusCode(505, "HTTP Version Not Supported"),
            new HttpStatusCode(506, "Variant Also Negotiates"),
            new HttpStatusCode(507, "Insufficient Storage (WebDAV)"),
            new HttpStatusCode(508, "Loop Detected (WebDAV)"),
            new HttpStatusCode(510, "Not Extended"),
            new HttpStatusCode(511, "Network Authentication Required"),

            // Unofficial codes.
            new HttpStatusCode(218, "This is fine (Apache HTTP Server, unofficial)"),
            new HttpStatusCode(419, "Page Expired (Laravel Framework, unofficial)"),
            new HttpStatusCode(420, "Method Failure (Spring Framework, unofficial)"),
            new HttpStatusCode(509, "Bandwidth Limit Exceeded (Apache Web Server/cPanel, unofficial)"),
            new HttpStatusCode(529, "Site is Overloaded (Qualys, unofficial)"),
            new HttpStatusCode(530, "Site is Frozen (Pantheon Systems, unofficial), or, See accompanying 1xxx error (Cloudflare, unofficial)"),
            new HttpStatusCode(598, "Network Read Timeout Error (unofficial)"),
            new HttpStatusCode(599, "Network Connect Timeout Error (unofficial)"),
            new HttpStatusCode(440, "Login Time-out (IIS, unofficial)"),
            new HttpStatusCode(449, "Retry With (IIS, unofficial)"),
            new HttpStatusCode(444, "No Response (nginx, unofficial)"),
            new HttpStatusCode(494, "Request header too large (nginx, unofficial)"),
            new HttpStatusCode(495, "SSL Certificate Error (nginx, unofficial)"),
            new HttpStatusCode(496, "SSL Certificate Required (nginx, unofficial)"),
            new HttpStatusCode(497, "HTTP Request Sent to HTTPS Port (nginx, unofficial)"),
            new HttpStatusCode(499, "Client Closed Request (nginx, unofficial)"),
            new HttpStatusCode(520, "Web Server Returned an Unknown Error (Cloudflare, unofficial)"),
            new HttpStatusCode(521, "Web Server Is Down (Cloudflare, unofficial)"),
            new HttpStatusCode(522, "Connection Timed Out (Cloudflare, unofficial)"),
            new HttpStatusCode(523, "Origin Is Unreachable (Cloudflare, unofficial)"),
            new HttpStatusCode(524, "A Timeout Occurred (Cloudflare, unofficial)"),
            new HttpStatusCode(525, "SSL Handshake Failed (Cloudflare, unofficial)"),
            new HttpStatusCode(526, "Invalid SSL Certificate (Cloudflare, unofficial)"),
            new HttpStatusCode(527, "Railgun Error (Cloudflare, unofficial)")
    };
}
