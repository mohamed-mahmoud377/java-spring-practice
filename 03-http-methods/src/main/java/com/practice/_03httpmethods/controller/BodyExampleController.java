package com.practice._03httpmethods.controller;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A guided tour of <b>request/response bodies and content types</b>.
 *
 * <p>The previous controller was about the HTTP verbs and where data comes from. This one
 * zooms into one of those places — the <b>body</b> — and shows that a body is just bytes
 * plus a label saying what those bytes are. That label is the <b>media type</b> (a.k.a.
 * MIME type / content type), e.g. {@code text/plain}, {@code application/json},
 * {@code application/xml}.</p>
 *
 * <h2>Two headers do all the work</h2>
 * <pre>
 *   Content-Type:  what the bytes I am SENDING are        (drives Spring's `consumes`)
 *   Accept:        what format I would LIKE back           (drives Spring's `produces`)
 * </pre>
 *
 * <h2>Two annotations map to them</h2>
 * <pre>
 *   consumes = "application/json"   → this method only accepts a JSON Content-Type
 *                                     (wrong type → 415 Unsupported Media Type)
 *   produces = "application/xml"    → this method can return XML
 *                                     (client asks for something else → 406 Not Acceptable)
 * </pre>
 *
 * <p>The magic to notice: the <b>same Java object</b> ({@link Greeting}) becomes JSON or XML
 * automatically depending on what the caller asks for. You write the object once; Spring's
 * "message converters" handle the format.</p>
 */
@RestController
@RequestMapping("/examples")
public class BodyExampleController {

    // =====================================================================
    // Data carriers. The XML root-element annotation only affects XML output
    // (the tag name); JSON ignores it.
    // =====================================================================

    @JacksonXmlRootElement(localName = "greeting")
    public record Greeting(String name, String message) {
    }

    /** JSON body used to open a support ticket in the "real world" example below. */
    public record NewTicket(String title, String severity) {
    }

    /** What we return after creating a ticket. */
    @JacksonXmlRootElement(localName = "ticket")
    public record Ticket(String id, String title, String severity, String status) {
    }

    // =====================================================================
    // 1) PLAIN TEXT — the simplest body there is.
    // =====================================================================

    /**
     * POST /examples/echo-text
     *
     * <p>Accepts a raw {@code text/plain} body and returns text. The body binds straight to
     * a {@code String} — no parsing, because there is no structure to parse.</p>
     *
     * <pre>curl -X POST localhost:8081/examples/echo-text \
     *      -H "Content-Type: text/plain" -d "hello there"</pre>
     */
    @PostMapping(value = "/echo-text",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String echoText(@RequestBody String body) {
        return "You sent " + body.length() + " characters: " + body;
    }

    // =====================================================================
    // 2) JSON — the default for REST APIs.
    // =====================================================================

    /**
     * POST /examples/json
     *
     * <p>Accepts a JSON object, Spring turns it into a {@link Greeting}, and we return a
     * JSON object back. Same idea as the previous controller, shown here for contrast.</p>
     *
     * <pre>curl -X POST localhost:8081/examples/json \
     *      -H "Content-Type: application/json" \
     *      -d '{"name":"Sara","message":"hi"}'</pre>
     */
    @PostMapping(value = "/json",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Greeting json(@RequestBody Greeting in) {
        return new Greeting(in.name(), "Hello " + in.name() + ", you said: " + in.message());
    }

    // =====================================================================
    // 3) XML — same object, different wire format.
    // =====================================================================

    /**
     * POST /examples/xml
     *
     * <p>Identical logic to {@code /json}, but it speaks XML. Notice the Java code is the
     * same shape — only the {@code consumes}/{@code produces} media types changed.</p>
     *
     * <pre>curl -X POST localhost:8081/examples/xml \
     *      -H "Content-Type: application/xml" -H "Accept: application/xml" \
     *      -d '&lt;Greeting&gt;&lt;name&gt;Sara&lt;/name&gt;&lt;message&gt;hi&lt;/message&gt;&lt;/Greeting&gt;'</pre>
     */
    @PostMapping(value = "/xml",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public Greeting xml(@RequestBody Greeting in) {
        return new Greeting(in.name(), "Hello " + in.name() + ", you said: " + in.message());
    }

    // =====================================================================
    // 4) THE STAR LESSON: one endpoint, JSON *or* XML, chosen by the caller.
    // =====================================================================

    /**
     * GET /examples/greeting?name=Sara
     *
     * <p>This single endpoint can return JSON or XML. It does not decide — the caller does,
     * with the {@code Accept} header. This is called <b>content negotiation</b>.</p>
     *
     * <pre>
     * curl localhost:8081/examples/greeting?name=Sara -H "Accept: application/json"
     * curl localhost:8081/examples/greeting?name=Sara -H "Accept: application/xml"
     * </pre>
     */
    @GetMapping(value = "/greeting",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public Greeting greeting(@RequestParam(defaultValue = "world") String name) {
        return new Greeting(name, "Hello, " + name + "!");
    }

    // =====================================================================
    // 5) FORM DATA — what an HTML <form> sends.
    // =====================================================================

    /**
     * POST /examples/form
     *
     * <p>A browser form submits {@code application/x-www-form-urlencoded} (fields joined like
     * a query string: {@code name=Sara&message=hi}). You read those fields with
     * {@code @RequestParam}, NOT {@code @RequestBody}.</p>
     *
     * <pre>curl -X POST localhost:8081/examples/form \
     *      -d "name=Sara" -d "message=hello"</pre>
     */
    @PostMapping(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Greeting form(@RequestParam String name,
                         @RequestParam(defaultValue = "(no message)") String message) {
        return new Greeting(name, "Form received from " + name + ": " + message);
    }

    // =====================================================================
    // 6) SAME PATH, DIFFERENT Content-Type → different method.
    //    Spring routes by the Content-Type header when paths collide.
    // =====================================================================

    @PostMapping(value = "/flexible", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String flexibleJson(@RequestBody Greeting in) {
        return "Handled as JSON. name=" + in.name();
    }

    @PostMapping(value = "/flexible", consumes = MediaType.TEXT_PLAIN_VALUE)
    public String flexibleText(@RequestBody String body) {
        return "Handled as plain text. body=" + body;
    }

    // =====================================================================
    // 7) BINARY — files, not text.
    // =====================================================================

    /**
     * GET /examples/download
     *
     * <p>Returns raw bytes with {@code application/octet-stream}. The
     * {@code Content-Disposition} header tells the browser to save it as a file. Real-world
     * use: exporting a report, serving a generated PDF, etc.</p>
     */
    @GetMapping(value = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> download() {
        byte[] content = "This file was generated by the server.\n".getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"note.txt\"")
                .body(content);
    }

    /**
     * POST /examples/upload
     *
     * <p>Accepts a file upload ({@code multipart/form-data} — how HTML file inputs are sent).
     * We don't store it; we just report what arrived. Real-world use: profile pictures,
     * document uploads.</p>
     *
     * <pre>curl -X POST localhost:8081/examples/upload -F "file=@./some-file.txt"</pre>
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("originalName", file.getOriginalFilename());
        report.put("contentType", file.getContentType());
        report.put("sizeBytes", file.getSize());
        // Peek at the first line just to prove we can read the bytes.
        String preview = new String(file.getBytes(), StandardCharsets.UTF_8);
        report.put("preview", preview.length() > 60 ? preview.substring(0, 60) + "…" : preview);
        return report;
    }

    // =====================================================================
    // 8) "Accept anything" — inspect whatever body/type arrives.
    // =====================================================================

    /**
     * POST /examples/echo
     *
     * <p>No {@code consumes} restriction, so it takes any body. It reads the raw bytes plus
     * the {@code Content-Type} header and reports both — handy for understanding what a
     * client is actually sending.</p>
     */
    @PostMapping("/echo")
    public Map<String, Object> echoAnything(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("receivedContentType", contentType == null ? "(none)" : contentType);
        report.put("byteCount", body == null ? 0 : body.length);
        report.put("bodyAsText", body == null ? "" : new String(body, StandardCharsets.UTF_8));
        return report;
    }

    // =====================================================================
    // 9) REAL-WORLD: create a resource, return 201 + Location + JSON/XML body.
    //    Combines Content-Type (in), Accept (out), status code, and a header.
    // =====================================================================

    /**
     * POST /examples/tickets
     *
     * <p>A realistic "create" endpoint. It reads a JSON body, and — thanks to
     * {@code produces} + {@link ResponseEntity} — returns the created ticket as JSON or XML
     * (whichever the {@code Accept} header requests), with HTTP <b>201 Created</b> and a
     * {@code Location} header pointing at the new resource. This is the shape most real REST
     * "create" endpoints take.</p>
     *
     * <pre>curl -i -X POST localhost:8081/examples/tickets \
     *      -H "Content-Type: application/json" -H "Accept: application/xml" \
     *      -d '{"title":"Printer broken","severity":"high"}'</pre>
     */
    @PostMapping(value = "/tickets",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<Ticket> createTicket(@RequestBody NewTicket in) {
        // A fake id — in a real app this would come from the database.
        String id = "TCK-" + Integer.toHexString((in.title() == null ? 0 : in.title().hashCode()) & 0xFFFF);
        Ticket created = new Ticket(id, in.title(),
                in.severity() == null ? "normal" : in.severity(), "OPEN");
        return ResponseEntity
                .created(URI.create("/examples/tickets/" + id)) // sets 201 + Location header
                .body(created);
    }
}
