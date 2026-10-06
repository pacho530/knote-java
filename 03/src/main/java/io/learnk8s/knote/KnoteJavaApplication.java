package io.learnk8s.knote;

import javax.annotation.PostConstruct;
import io.minio.MinioClient;
import org.apache.commons.io.IOUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import java.io.InputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;


import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class KnoteJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnoteJavaApplication.class, args);
    }

}

interface NotesRepository extends MongoRepository<Note, String> {

}

@Document(collection = "notes")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
class Note {
    @Id
    private String id;
    private String description;

    @Override
    public String toString() {
        return description;
    }
}

@Configuration
@EnableConfigurationProperties(KnoteProperties.class)
class KnoteConfig {

}



@ConfigurationProperties(prefix = "knote")
class KnoteProperties {

    @Value("${knote.minio.host:localhost}")
    private String minioHost;

    @Value("${knote.minio.bucket:image-storage}")
    private String minioBucket;

    @Value("${knote.minio.access.key:mykey}")
    private String minioAccessKey;

    @Value("${knote.minio.secret.key:mysecret}")
    private String minioSecretKey;

    @Value("${knote.minio.useSSL:false}")
    private boolean minioUseSSL;

    @Value("${knote.minio.reconnect.enabled:true}")
    private boolean minioReconnectEnabled;

    public String getMinioHost() {
        return minioHost;
    }

    public String getMinioBucket() {
        return minioBucket;
    }

    public String getMinioAccessKey() {
        return minioAccessKey;
    }

    public String getMinioSecretKey() {
        return minioSecretKey;
    }

    public boolean isMinioUseSSL() {
        return minioUseSSL;
    }

    public boolean isMinioReconnectEnabled() {
        return minioReconnectEnabled;
    }
}

@Controller
class KNoteController {

    @Autowired
    private NotesRepository notesRepository;
    @Autowired
    private KnoteProperties properties;
    private MinioClient minioClient;
    private Parser parser = Parser.builder().build();
    private HtmlRenderer renderer = HtmlRenderer.builder().build();
    @PostConstruct
    public void initMinio() throws Exception {
        minioClient = new MinioClient(
                "http://" + properties.getMinioHost() + ":9000",
                properties.getMinioAccessKey(),
                properties.getMinioSecretKey(),
                properties.isMinioUseSSL()
        );

        if (!minioClient.bucketExists(properties.getMinioBucket())) {
        minioClient.makeBucket(properties.getMinioBucket());
        }
    }


    @GetMapping("/")
    public String index(Model model) {
        getAllNotes(model);
        return "index";
    }

    @PostMapping("/note")
    public String saveNotes(@RequestParam("image") MultipartFile file,
                            @RequestParam String description,
                            @RequestParam(required = false) String publish,
                            @RequestParam(required = false) String upload,
                            Model model) throws Exception {

        if (publish != null && publish.equals("Publish")) {
            saveNote(description, model);
            getAllNotes(model);
            return "redirect:/";
        }
        if (upload != null && upload.equals("Upload")) {
            if (file != null && file.getOriginalFilename() != null &&
                    !file.getOriginalFilename().isEmpty()) {
                uploadImage(file, description, model);
            }
            getAllNotes(model);
            return "index";
        }
        return "index";
    }


    private void getAllNotes(Model model) {
        List<Note> notes = notesRepository.findAll();
        Collections.reverse(notes);
        model.addAttribute("notes", notes);
    }

    private void uploadImage(MultipartFile file, String description, Model model) throws Exception {
    String fileId = UUID.randomUUID().toString() + "." +
            file.getOriginalFilename().split("\\.")[1];

    minioClient.putObject(
            properties.getMinioBucket(),
            fileId,
            file.getInputStream(),
            file.getSize(),
            null,
            null,
            file.getContentType()
    );

    model.addAttribute("description",
            description + " ![](/img/" + fileId + ")");
}

    @GetMapping(value = "/img/{name}", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] getImageByName(@PathVariable String name) throws Exception {
        InputStream imageStream = minioClient.getObject(
                properties.getMinioBucket(),
                name
        );

        return IOUtils.toByteArray(imageStream);
    }
    private void saveNote(String description, Model model) {
        if (description != null && !description.trim().isEmpty()) {
            //We need to translate markup to HTML
            Node document = parser.parse(description.trim());
            String html = renderer.render(document);
            notesRepository.save(new Note(null, html));
            //After publish you need to clean up the textarea
            model.addAttribute("description", "");
        }
    }

}
