package com.example.TextileManagement.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.service.CompanyAccessService;
import com.example.TextileManagement.service.PrivateObjectStorageService;
import com.example.TextileManagement.service.WorkspaceRoleAccessService;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {
    @Mock
    private CompanyProfileRepository repository;

    @Mock
    private CurrentCompanyContext companyContext;

    @Mock
    private CompanyAccessService companyAccessService;

    @Mock
    private WorkspaceRoleAccessService workspaceRoleAccessService;

    @Mock
    private PrivateObjectStorageService objectStorage;

    private CompanyController controller;
    private CompanyProfile profile;
    private Authentication owner;

    @BeforeEach
    void setUp() {
        controller = new CompanyController(repository, companyContext, companyAccessService, workspaceRoleAccessService,
                objectStorage);
        profile = new CompanyProfile();
        when(companyContext.getCompanyId()).thenReturn(10L);
        owner = new UsernamePasswordAuthenticationToken("owner@example.com", null);
    }

    @Test
    void acceptsImageContentEvenWhenTheUploadedMimeTypeIsUntrusted() throws Exception {
        byte[] png = png(24, 16);
        MockMultipartFile file = new MockMultipartFile("file", "logo.txt", "text/plain", png);

        when(workspaceRoleAccessService.canManageCompanySettings("owner@example.com", 10L)).thenReturn(true);
        when(repository.findById(10L)).thenReturn(Optional.of(profile));
        controller.updateCompanyLogo(file, owner);

        assertArrayEquals(png, profile.getLogoData());
        assertEquals("image/png", profile.getLogoContentType());
        assertEquals(24, profile.getLogoWidth());
        assertEquals(16, profile.getLogoHeight());
    }

    @Test
    void rejectsNonImageBytes() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "not-an-image".getBytes());

        when(workspaceRoleAccessService.canManageCompanySettings("owner@example.com", 10L)).thenReturn(true);
        when(repository.findById(10L)).thenReturn(Optional.of(profile));
        assertThrows(ResponseStatusException.class, () -> controller.updateCompanyLogo(file, owner));
    }

    @Test
    void rejectsOversizedImageMetadataBeforeDecode() throws Exception {
        byte[] bytes = png(1, 1);
        writeInt(bytes, 16, 3000);
        writeInt(bytes, 20, 3000);
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", bytes);

        when(workspaceRoleAccessService.canManageCompanySettings("owner@example.com", 10L)).thenReturn(true);
        when(repository.findById(10L)).thenReturn(Optional.of(profile));

        assertThrows(ResponseStatusException.class, () -> controller.updateCompanyLogo(file, owner));
    }

    @Test
    void resizesWideLogoBeforeStorage() throws Exception {
        byte[] original = png(3000, 1000);
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", original);

        when(workspaceRoleAccessService.canManageCompanySettings("owner@example.com", 10L)).thenReturn(true);
        when(repository.findById(10L)).thenReturn(Optional.of(profile));
        controller.updateCompanyLogo(file, owner);

        assertEquals(2000, profile.getLogoWidth());
        assertEquals(667, profile.getLogoHeight());
        BufferedImage stored = ImageIO.read(new java.io.ByteArrayInputStream(profile.getLogoData()));
        assertEquals(2000, stored.getWidth());
        assertEquals(667, stored.getHeight());
    }

    @Test
    void readsStoredLogoWithoutRedirectingToObjectStorage() {
        byte[] logo = new byte[] {1, 2, 3};
        profile.setLogoStorageKey("companies/10/logos/logo");
        profile.setLogoContentType("image/png");
        when(repository.findById(10L)).thenReturn(Optional.of(profile));
        when(objectStorage.isEnabled()).thenReturn(true);
        when(objectStorage.read(profile.getLogoStorageKey())).thenReturn(logo);

        ResponseEntity<byte[]> response = controller.getCompanyLogo();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getCacheControl().contains("private"));
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=31536000"));
        assertArrayEquals(logo, response.getBody());
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
