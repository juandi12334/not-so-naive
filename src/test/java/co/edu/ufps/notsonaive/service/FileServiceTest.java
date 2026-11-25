package co.edu.ufps.notsonaive.service;

import co.edu.ufps.notsonaive.exception.InvalidFileException;
import co.edu.ufps.notsonaive.model.TextDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {

    private final FileService service = new FileService(2 * 1024 * 1024);

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "text/plain", content);
    }

    @Test
    void readsPlainAsciiFile() {
        TextDocument doc = service.read(file("a.txt", "Hello\nWorld\n".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("Hello\nWorld\n", doc.content());
        assertEquals(2, doc.lineCount());
        assertTrue(doc.notices().esVacia());
    }

    @Test
    void rejectsEmptyFile() {
        InvalidFileException e = assertThrows(InvalidFileException.class, () -> service.read(file("empty.txt", new byte[0])));
        assertTrue(e.getMessage().contains("empty"));
    }

    @Test
    void rejectsMissingFile() {
        assertThrows(InvalidFileException.class, () -> service.read(null));
    }

    @Test
    void rejectsNonTxtExtension() {
        assertThrows(InvalidFileException.class, () -> service.read(file("data.pdf", "abc".getBytes())));
        assertThrows(InvalidFileException.class, () -> service.read(file("notes.txt.exe", "abc".getBytes())));
    }

    @Test
    void rejectsUtf8NonAsciiWithLineAndColumn() {
        byte[] utf8 = "ok\nCanción".getBytes(StandardCharsets.UTF_8);
        InvalidFileException e = assertThrows(InvalidFileException.class, () -> service.read(file("u.txt", utf8)));
        assertTrue(e.getMessage().contains("not ASCII"), e.getMessage());
        assertTrue(e.getMessage().contains("line 2, column 6"), e.getMessage());
    }

    @Test
    void rejectsLatin1() {
        byte[] latin1 = "año".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(InvalidFileException.class, () -> service.read(file("l.txt", latin1)));
    }

    @Test
    void rejectsBinaryControlBytes() {
        byte[] bin = {'P', 'K', 3, 4, 0, 0};
        InvalidFileException e = assertThrows(InvalidFileException.class, () -> service.read(file("zip.txt", bin)));
        assertTrue(e.getMessage().contains("control byte"));
    }

    @Test
    void rejectsTooLargeFile() {
        FileService small = new FileService(10);
        assertThrows(InvalidFileException.class, () -> small.read(file("big.txt", "01234567890".getBytes())));
    }

    @Test
    void normalizesCrLfAndReportsIt() {
        TextDocument doc = service.read(file("w.txt", "a\r\nb\rc".getBytes()));
        assertEquals("a\nb\nc", doc.content());
        assertEquals(3, doc.lineCount());
        assertFalse(doc.notices().esVacia());
    }

    @Test
    void stripsUtf8BomWhenRestIsAscii() {
        byte[] withBom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};
        TextDocument doc = service.read(file("bom.txt", withBom));
        assertEquals("hi", doc.content());
        assertEquals(1, doc.notices().getTamanio());
    }

    @Test
    void acceptsTabsAndAllPrintableAscii() {
        StringBuilder sb = new StringBuilder("\t");
        for (char c = 0x20; c <= 0x7E; c++) {
            sb.append(c);
        }
        TextDocument doc = service.read(file("all.txt", sb.toString().getBytes(StandardCharsets.US_ASCII)));
        assertEquals(sb.toString(), doc.content());
    }

    @Test
    void typedTextIsAcceptedAndNormalized() {
        TextDocument doc = service.fromTypedText("BANANA\r\nANA");
        assertEquals("BANANA\nANA", doc.content());
        assertEquals(FileService.TYPED_TEXT_NAME, doc.fileName());
    }

    @Test
    void typedTextRules() {
        assertThrows(InvalidFileException.class, () -> service.fromTypedText(""));
        assertThrows(InvalidFileException.class, () -> service.fromTypedText(null));
        InvalidFileException e = assertThrows(InvalidFileException.class, () -> service.fromTypedText("ok\nCanci\u00f3n"));
        assertTrue(e.getMessage().contains("line 2, column 6"), e.getMessage());
        FileService small = new FileService(5);
        assertThrows(InvalidFileException.class, () -> small.fromTypedText("123456"));
    }
}
