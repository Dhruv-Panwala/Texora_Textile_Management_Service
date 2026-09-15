package com.example.TextileManagement.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Iterator;
import java.util.UUID;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.service.CompanyAccessService;
import com.example.TextileManagement.service.WorkspaceRoleAccessService;
import com.example.TextileManagement.service.PrivateObjectStorageService;
import com.example.TextileManagement.service.VersionConflict;

@RestController
@RequestMapping("/api/company")
public class CompanyController {
    private static final int MAX_LOGO_BYTES = 1024 * 1024;
    private static final int MAX_LOGO_DIMENSION = 2000;
    private static final int MAX_SOURCE_LOGO_DIMENSION = 4000;
    private static final long MAX_LOGO_PIXELS = 4_000_000L;
    private final CompanyProfileRepository repository;
    private final CurrentCompanyContext currentCompanyContext;
    private final CompanyAccessService companyAccessService;
    private final WorkspaceRoleAccessService workspaceRoleAccessService;
    private final PrivateObjectStorageService objectStorage;

    public CompanyController(CompanyProfileRepository repository, CurrentCompanyContext currentCompanyContext,
            CompanyAccessService companyAccessService, WorkspaceRoleAccessService workspaceRoleAccessService,
            PrivateObjectStorageService objectStorage) {
        this.repository = repository;
        this.currentCompanyContext = currentCompanyContext;
        this.companyAccessService = companyAccessService;
        this.workspaceRoleAccessService = workspaceRoleAccessService;
        this.objectStorage = objectStorage;
    }

    @GetMapping
    public CompanyProfile getCompanyProfile() {
        return repository.findById(currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not found"));
    }

    @GetMapping("/all")
    public List<CompanyProfile> getCompanyProfiles(Authentication authentication) {
        return companyAccessService.findAccessibleCompanies(authentication.getName());
    }

    @GetMapping("/{id}")
    public CompanyProfile getCompanyProfileById(@PathVariable Long id, Authentication authentication) {
        requireAccess(authentication, id);
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not found"));
    }

    @PutMapping
    public CompanyProfile updateCompanyProfile(@RequestBody CompanyProfile request, Authentication authentication) {
        return updateCompanyProfile(requireCompanySettingsAccess(authentication), request);
    }

    private CompanyProfile updateCompanyProfile(Long id, CompanyProfile request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company details are required");
        }
        CompanyProfile profile = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not found"));
        VersionConflict.requireCurrent(request.getVersion(), profile.getVersion());
        String tradeName = request.getTradeName() == null ? "" : request.getTradeName().trim();
        if (tradeName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trade name is required");
        }
        repository.findByWorkspace_IdAndTradeNameIgnoreCaseAndIdNot(profile.getWorkspace().getId(), tradeName, id).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trade name already exists");
        });
        profile.setTradeName(tradeName);
        profile.setGstNo(trimToNull(request.getGstNo()));
        profile.setPhone(trimToNull(request.getPhone()));
        profile.setAddress(trimToNull(request.getAddress()));
        profile.setDefaultBroker(trimToNull(request.getDefaultBroker()));
        profile.setDefaultQuality(trimToNull(request.getDefaultQuality()));
        return repository.save(profile);
    }

    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanyProfile updateCompanyLogo(@RequestParam("file") MultipartFile file, Authentication authentication) {
        CompanyProfile profile = repository.findById(requireCompanySettingsAccess(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not found"));
        LogoDetails logo = validateLogo(file);
        String oldStorageKey = profile.getLogoStorageKey();
        if (objectStorage.isEnabled()) {
            String storageKey = "companies/" + profile.getId() + "/logos/" + UUID.randomUUID();
            objectStorage.put(storageKey, logo.bytes(), logo.contentType());
            profile.setLogoStorageKey(storageKey);
            profile.setLogoData(null);
        } else {
            profile.setLogoStorageKey(null);
            profile.setLogoData(logo.bytes());
        }
        profile.setLogoContentType(logo.contentType());
        profile.setLogoWidth(logo.width());
        profile.setLogoHeight(logo.height());
        CompanyProfile saved = repository.save(profile);
        if (oldStorageKey != null && objectStorage.isEnabled()) {
            objectStorage.delete(oldStorageKey);
        }
        return saved;
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> getCompanyLogo() {
        CompanyProfile profile = repository.findById(currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not found"));
        if (profile.getLogoStorageKey() != null && objectStorage.isEnabled()) {
            return ResponseEntity.ok()
                    .cacheControl(logoCacheControl())
                    .contentType(logoContentType(profile))
                    .body(objectStorage.read(profile.getLogoStorageKey()));
        }
        if (profile.getLogoData() == null || profile.getLogoContentType() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(logoCacheControl())
                .contentType(logoContentType(profile))
                .body(profile.getLogoData());
    }

    @DeleteMapping("/logo")
    public ResponseEntity<Void> deleteCompanyLogo(Authentication authentication) {
        CompanyProfile profile = repository.findById(requireCompanySettingsAccess(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not found"));
        if (profile.getLogoStorageKey() != null && objectStorage.isEnabled()) {
            objectStorage.delete(profile.getLogoStorageKey());
        }
        profile.setLogoData(null);
        profile.setLogoStorageKey(null);
        profile.setLogoContentType(null);
        profile.setLogoWidth(null);
        profile.setLogoHeight(null);
        repository.save(profile);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public CompanyProfile createCompanyProfile(@RequestBody CompanyProfile request, Authentication authentication) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company details are required");
        }
        CompanyProfile profile = new CompanyProfile();
        profile.setTradeName(trimToNull(request.getTradeName()));
        profile.setGstNo(trimToNull(request.getGstNo()));
        profile.setPhone(trimToNull(request.getPhone()));
        profile.setAddress(trimToNull(request.getAddress()));
        profile.setDefaultBroker(trimToNull(request.getDefaultBroker()));
        profile.setDefaultQuality(trimToNull(request.getDefaultQuality()));
        if (profile.getTradeName() == null || profile.getTradeName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trade name is required");
        }
        CompanyProfile current = repository.findById(currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"));
        if (authentication == null || !workspaceRoleAccessService.canManageWorkspace(authentication.getName(), current.getWorkspace().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner or admin access is required");
        }
        profile.setWorkspace(current.getWorkspace());
        repository.findByWorkspace_IdAndTradeNameIgnoreCase(current.getWorkspace().getId(), profile.getTradeName()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trade name already exists");
        });
        return repository.save(profile);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MediaType logoContentType(CompanyProfile profile) {
        return profile.getLogoContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(profile.getLogoContentType());
    }

    private Long currentCompanyId() {
        Long companyId = currentCompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required");
        }
        return companyId;
    }

    private void requireAccess(Authentication authentication, Long companyId) {
        if (authentication == null || !companyAccessService.canAccess(authentication.getName(), companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private Long requireCompanySettingsAccess(Authentication authentication) {
        Long companyId = currentCompanyId();
        if (authentication == null
                || !workspaceRoleAccessService.canManageCompanySettings(authentication.getName(), companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Owner or admin access is required to change company settings");
        }
        return companyId;
    }

    private LogoDetails validateLogo(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_LOGO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo must be a non-empty image up to 1 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            String contentType = detectContentType(bytes);
            if (contentType == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Logo must be a PNG or JPEG no larger than 2000 x 2000 pixels");
            }
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (input == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo could not be read");
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo could not be read");
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > MAX_SOURCE_LOGO_DIMENSION
                            || height > MAX_SOURCE_LOGO_DIMENSION
                            || (long) width * height > MAX_LOGO_PIXELS) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Logo must be a bounded PNG or JPEG image no larger than 1 MB");
                    }
                    BufferedImage image = reader.read(0);
                    if (image == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo could not be read");
                    }
                    LogoDetails resized = resizeLogo(image, contentType, width, height);
                    return resized == null ? new LogoDetails(bytes, contentType, width, height) : resized;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo could not be read");
        }
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "image/png";
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    private LogoDetails resizeLogo(BufferedImage source, String contentType, int width, int height) {
        if (width <= MAX_LOGO_DIMENSION && height <= MAX_LOGO_DIMENSION) {
            return null;
        }
        double scale = Math.min((double) MAX_LOGO_DIMENSION / width, (double) MAX_LOGO_DIMENSION / height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        int imageType = "image/png".equals(contentType) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (imageType == BufferedImage.TYPE_INT_RGB) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        String format = "image/png".equals(contentType) ? "png" : "jpg";
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(resized, format, output)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo could not be encoded");
            }
            return new LogoDetails(output.toByteArray(), contentType, targetWidth, targetHeight);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo could not be encoded");
        }
    }

    private CacheControl logoCacheControl() {
        return CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();
    }

    private record LogoDetails(byte[] bytes, String contentType, int width, int height) {
    }
}
