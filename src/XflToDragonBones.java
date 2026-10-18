import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class XflToDragonBones {

    private static final String XFL_NS =
            "http://ns.adobe.com/xfl/2008/";

    private final File xflRoot;

    // library path -> actual XML/PNG file
    private final Map<String, File> libraryFiles = new LinkedHashMap<>();

    // Parsed XFL symbols
    private final Map<String, XflSymbol> symbols =
            new LinkedHashMap<>();

    // Resolved XFL document data
    private final Map<String, XflMedia> media =
            new LinkedHashMap<>();

    private final Map<String, String> imageSlotNames =
            new LinkedHashMap<>();

    private final Map<String, XflInstance> imageSlotInstances =
            new LinkedHashMap<>();

    private final Map<String, String> slotSourceImage = new LinkedHashMap<>();

    public XflToDragonBones(File xflRoot) {
        this.xflRoot = xflRoot;
    }

    // ============================================================
    // ENTRY POINT
    // ============================================================

    public void parse() throws Exception {

        scanLibrary();

        System.out.println("Library files: " + libraryFiles.size());
        System.out.println();

        parseAllSymbols();

// Resolve every DOMSymbolInstance -> XflSymbol
        resolveReferences();

// Build the structural children from the XFL symbols
        buildAllStructuralChildren();

        printSummary();
        printSymbolHierarchy();

    }

    // ============================================================
    // LIBRARY SCANNER
    // ============================================================

    private void scanLibrary() {

        File library = new File(xflRoot, "library");

        if (!library.exists()) {
            throw new RuntimeException(
                    "Library directory not found: " + library
            );
        }

        scanDirectory(library);
    }

    private void scanDirectory(File directory) {

        File[] files = directory.listFiles();

        if (files == null)
            return;

        for (File file : files) {

            if (file.isDirectory()) {
                scanDirectory(file);
                continue;
            }

            String relative =
                    xflRoot.toPath()
                            .relativize(file.toPath())
                            .toString()
                            .replace(File.separatorChar, '/');

            if (!relative.startsWith("library/"))
                continue;

            String libraryName =
                    relative.substring("library/".length());

            // Remove extension
            int dot = libraryName.lastIndexOf('.');

            if (dot != -1)
                libraryName =
                        libraryName.substring(0, dot);

            libraryFiles.put(libraryName, file);
        }
    }

    // ============================================================
    // PARSE ALL SYMBOLS
    // ============================================================

    private void parseAllSymbols() throws Exception {

        for (Map.Entry<String, File> entry :
                libraryFiles.entrySet()) {

            String name = entry.getKey();
            File file = entry.getValue();

            if (!file.getName().endsWith(".xml"))
                continue;

            try {

                Document document = parseXML(file);

                Element root =
                        document.getDocumentElement();

                String tag =
                        root.getLocalName();

                if (!"DOMSymbolItem".equals(tag))
                    continue;

                XflSymbol symbol =
                        parseSymbol(name, root);

                symbols.put(name, symbol);

            } catch (Exception e) {

                System.err.println(
                        "Failed to parse: " + name
                );

                e.printStackTrace();
            }
        }
    }

    // ============================================================
    // SYMBOL
    // ============================================================

    private XflSymbol parseSymbol(
            String libraryName,
            Element root
    ) throws Exception {

        XflSymbol symbol = new XflSymbol();

        symbol.libraryName = libraryName;
        symbol.name = root.getAttribute("name");
        symbol.symbolType = root.getAttribute("symbolType");

        NodeList timelines =
                root.getElementsByTagNameNS(
                        XFL_NS,
                        "DOMTimeline"
                );

        for (int i = 0; i < timelines.getLength(); i++) {

            Element timelineElement =
                    (Element) timelines.item(i);

            symbol.timelines.add(
                    parseTimeline(timelineElement)
            );
        }

        return symbol;
    }

    // ============================================================
    // TIMELINE
    // ============================================================

    private XflTimeline parseTimeline(
            Element element
    ) {

        XflTimeline timeline =
                new XflTimeline();

        timeline.name =
                element.getAttribute("name");

        NodeList layers =
                element.getElementsByTagNameNS(
                        XFL_NS,
                        "DOMLayer"
                );

        for (int i = 0; i < layers.getLength(); i++) {

            Element layerElement =
                    (Element) layers.item(i);

            XflLayer layer =
                    parseLayer(layerElement);

            timeline.layers.add(layer);
        }

        return timeline;
    }

    // ============================================================
    // LAYER
    // ============================================================

    private XflLayer parseLayer(
            Element element
    ) {

        XflLayer layer =
                new XflLayer();

        layer.name =
                element.getAttribute("name");

        layer.layerType =
                element.getAttribute("layerType");

        layer.color =
                element.getAttribute("color");

        NodeList frames =
                element.getElementsByTagNameNS(
                        XFL_NS,
                        "DOMFrame"
                );

        for (int i = 0; i < frames.getLength(); i++) {

            Element frameElement =
                    (Element) frames.item(i);

            XflFrame frame =
                    parseFrame(frameElement);

            layer.frames.add(frame);
        }

        return layer;
    }

    // ============================================================
    // FRAME
    // ============================================================

    private XflFrame parseFrame(
            Element element
    ) {

        XflFrame frame =
                new XflFrame();

        String index =
                element.getAttribute("index");

        String duration =
                element.getAttribute("duration");

        frame.index =
                parseInt(index, 0);

        frame.duration =
                parseInt(duration, 1);

        frame.tweenType =
                element.getAttribute("tweenType");

        frame.motionTweenRotate =
                element.getAttribute(
                        "motionTweenRotate"
                );

        frame.motionTweenSnap =
                element.getAttribute(
                        "motionTweenSnap"
                );

        frame.tweenEasing =
                element.getAttribute(
                        "tweenEasing"
                );

        // --------------------------------------------------------
        // Symbol instances
        // --------------------------------------------------------

        NodeList instances =
                element.getElementsByTagNameNS(
                        XFL_NS,
                        "DOMSymbolInstance"
                );

        for (int i = 0; i < instances.getLength(); i++) {

            Element instanceElement =
                    (Element) instances.item(i);

            XflInstance instance =
                    parseInstance(instanceElement);

            frame.instances.add(instance);
        }

        NodeList bitmapInstances =
                element.getElementsByTagNameNS(
                        XFL_NS,
                        "DOMBitmapInstance"
                );

        for (int i = 0; i < bitmapInstances.getLength(); i++) {

            Element bitmapElement =
                    (Element) bitmapInstances.item(i);

            XflInstance bitmapInstance =
                    parseBitmapInstance(bitmapElement);

            frame.instances.add(bitmapInstance);
        }

        return frame;
    }

    // ============================================================
    // SYMBOL INSTANCE
    // ============================================================

    private XflInstance parseInstance(
            Element element
    ) {

        XflInstance instance =
                new XflInstance();

        // --------------------------------------------------------
        // Basic fields
        // --------------------------------------------------------

        instance.libraryItemName =
                element.getAttribute(
                        "libraryItemName"
                );

        instance.name =
                element.getAttribute("name");

        instance.symbolType =
                element.getAttribute("symbolType");

        instance.loop =
                element.getAttribute("loop");

        instance.firstFrame =
                parseInt(
                        element.getAttribute("firstFrame"),
                        0
                );

        // --------------------------------------------------------
        // Matrix
        // --------------------------------------------------------

        Element matrixWrapper =
                getDirectChild(
                        element,
                        "matrix"
                );

        if (matrixWrapper != null) {

            Element matrixElement =
                    getDirectChild(
                            matrixWrapper,
                            "Matrix"
                    );

            if (matrixElement != null) {

                instance.matrix =
                        parseMatrix(matrixElement);
            }
        }
        // --------------------------------------------------------
        // Color
        // --------------------------------------------------------

        Element colorWrapper =
                getDirectChild(
                        element,
                        "color"
                );

        if (colorWrapper != null) {

            Element colorElement =
                    getDirectChild(
                            colorWrapper,
                            "Color"
                    );

            if (colorElement != null) {

                instance.color =
                        parseColor(colorElement);
            }
        }

        return instance;
    }

    private XflInstance parseBitmapInstance(Element element) {

        XflInstance instance = new XflInstance();

        instance.libraryItemName =
                element.getAttribute("libraryItemName");

        Element matrixWrapper =
                getDirectChild(element, "matrix");

        if (matrixWrapper != null) {

            Element matrixElement =
                    getDirectChild(matrixWrapper, "Matrix");

            if (matrixElement != null) {
                instance.matrix = parseMatrix(matrixElement);
            }
        }

        return instance;
    }

    // ============================================================
    // MATRIX
    // ============================================================

    private XflMatrix parseMatrix(
            Element element
    ) {

        XflMatrix matrix =
                new XflMatrix();

        matrix.a =
                parseDouble(
                        element.getAttribute("a"),
                        1
                );

        matrix.b =
                parseDouble(
                        element.getAttribute("b"),
                        0
                );

        matrix.c =
                parseDouble(
                        element.getAttribute("c"),
                        0
                );

        matrix.d =
                parseDouble(
                        element.getAttribute("d"),
                        1
                );

        matrix.tx =
                parseDouble(
                        element.getAttribute("tx"),
                        0
                );

        matrix.ty =
                parseDouble(
                        element.getAttribute("ty"),
                        0
                );

        return matrix;
    }

    // ============================================================
    // COLOR
    // ============================================================

    private XflColor parseColor(
            Element element
    ) {

        XflColor color =
                new XflColor();

        color.alphaMultiplier =
                parseDouble(
                        element.getAttribute(
                                "alphaMultiplier"
                        ),
                        1
                );

        color.redMultiplier =
                parseDouble(
                        element.getAttribute(
                                "redMultiplier"
                        ),
                        1
                );

        color.greenMultiplier =
                parseDouble(
                        element.getAttribute(
                                "greenMultiplier"
                        ),
                        1
                );

        color.blueMultiplier =
                parseDouble(
                        element.getAttribute(
                                "blueMultiplier"
                        ),
                        1
                );

        color.alphaOffset =
                parseDouble(
                        element.getAttribute(
                                "alphaOffset"
                        ),
                        0
                );

        color.redOffset =
                parseDouble(
                        element.getAttribute(
                                "redOffset"
                        ),
                        0
                );

        color.greenOffset =
                parseDouble(
                        element.getAttribute(
                                "greenOffset"
                        ),
                        0
                );

        color.blueOffset =
                parseDouble(
                        element.getAttribute(
                                "blueOffset"
                        ),
                        0
                );

        return color;
    }

    // ============================================================
    // XML HELPERS
    // ============================================================

    private Document parseXML(File file)
            throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        return builder.parse(file);
    }

    private Element getDirectChild(
            Element parent,
            String localName
    ) {

        NodeList children =
                parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {

            Node node =
                    children.item(i);

            if (node.getNodeType()
                    != Node.ELEMENT_NODE)
                continue;

            Element element =
                    (Element) node;

            if (localName.equals(
                    element.getLocalName()
            )) {
                return element;
            }
        }

        return null;
    }

    private int parseInt(
            String value,
            int fallback
    ) {

        if (value == null || value.isEmpty())
            return fallback;

        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parseDouble(
            String value,
            double fallback
    ) {

        if (value == null || value.isEmpty())
            return fallback;

        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    // ============================================================
    // SUMMARY
    // ============================================================

    private void printSummary() {

        int symbolCount = symbols.size();
        int timelineCount = 0;
        int layerCount = 0;
        int frameCount = 0;
        int instanceCount = 0;

        for (XflSymbol symbol : symbols.values()) {

            timelineCount += symbol.timelines.size();

            for (XflTimeline timeline :
                    symbol.timelines) {

                layerCount += timeline.layers.size();

                for (XflLayer layer :
                        timeline.layers) {

                    frameCount += layer.frames.size();

                    for (XflFrame frame :
                            layer.frames) {

                        instanceCount +=
                                frame.instances.size();
                    }
                }
            }
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("PARSE SUMMARY");
        System.out.println("========================================");
        System.out.println("Library entries : " +
                libraryFiles.size());
        System.out.println("Symbols         : " +
                symbolCount);
        System.out.println("Timelines       : " +
                timelineCount);
        System.out.println("Layers          : " +
                layerCount);
        System.out.println("Frames          : " +
                frameCount);
        System.out.println("Instances       : " +
                instanceCount);
        System.out.println("========================================");
        System.out.println();

        // Print important symbols
        for (XflSymbol symbol : symbols.values()) {

            if (symbol.libraryName.startsWith("label/")) {

                System.out.println(
                        "Animation: " +
                                symbol.libraryName
                );

                for (XflTimeline timeline :
                        symbol.timelines) {

                    System.out.println(
                            "  Timeline: " +
                                    timeline.name
                    );

                    for (XflLayer layer :
                            timeline.layers) {

                        System.out.println(
                                "    Layer: " +
                                        layer.name +
                                        " frames=" +
                                        layer.frames.size()
                        );
                    }
                }
            }
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("ALL SYMBOLS");
        System.out.println("========================================");

        for (XflSymbol symbol : symbols.values()) {

            System.out.println(
                    symbol.libraryName
                            + " | type=" + symbol.symbolType
                            + " | timelines=" + symbol.timelines.size()
            );
        }
    }

    // ============================================================
    // DATA CLASSES
    // ============================================================

    static class XflMedia {

        String name;
        String path;

        int width;
        int height;
    }



    static class XflSymbol {

        String libraryName;
        String name;
        String symbolType;

        // Structural contents
        List<XflInstance> children =
                new ArrayList<>();

        // Timelines
        List<XflTimeline> timelines =
                new ArrayList<>();
    }

    static class XflTimeline {

        String name;

        List<XflLayer> layers =
                new ArrayList<>();
    }

    static class XflLayer {

        String name;
        String layerType;
        String color;

        List<XflFrame> frames =
                new ArrayList<>();
    }

    static class XflFrame {

        int index;
        int duration;

        String tweenType;
        String motionTweenRotate;
        String motionTweenSnap;
        String tweenEasing;

        List<XflInstance> instances =
                new ArrayList<>();
    }

    static class XflInstance {

        XflSymbol referencedSymbol;
        String libraryItemName;
        String name;
        String symbolType;
        String loop;

        int firstFrame;

        XflMatrix matrix;
        XflColor color;
    }

    static class XflMatrix {

        double a;
        double b;
        double c;
        double d;
        double tx;
        double ty;

        // --------------------------------------------------------
        // Derived transform values
        // --------------------------------------------------------

        public double getScaleX() {

            return Math.sqrt(
                    a * a +
                            b * b
            );
        }

        double getScaleY() {

            double scaleX = getScaleX();

            if (scaleX == 0)
                return 0;

            double determinant =
                    a * d - b * c;

            return determinant / scaleX;
        }

        double getRotationDegrees() {

            return Math.toDegrees(
                    Math.atan2(b, a)
            );
        }

        double getSkX() {
            return Math.atan2(b, a);
        }

        double getSkY() {
            return Math.atan2(-c, d);
        }

        double getSkewXDegrees() {

            double scaleX = getScaleX();

            if (scaleX == 0)
                return 0;

            return Math.toDegrees(
                    Math.atan2(
                            a * c + b * d,
                            scaleX * scaleX
                    )
            );
        }
    }

    static class XflColor {

        double alphaMultiplier;
        double redMultiplier;
        double greenMultiplier;
        double blueMultiplier;

        double alphaOffset;
        double redOffset;
        double greenOffset;
        double blueOffset;
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args)
            throws Exception {

        File root = new File(
                "D:\\pvz2 pvp\\pvz2 obb\\packet\\PlantPeashooter.package\\resource\\images\\initial\\plant\\peashooter"
        );

        if (!root.exists()) {

            System.err.println(
                    "XFL directory does not exist:"
            );

            System.err.println(
                    root.getAbsolutePath()
            );

            return;
        }

        XflToDragonBones converter =
                new XflToDragonBones(root);

        converter.scanLibrary();

        converter.parseAllSymbols();

        converter.buildAllStructuralChildren();

        XflSymbol custom =
                converter.symbols.get("sprite/_custom");

        System.out.println(
                "_custom children = " +
                        (custom == null ? "NULL" : custom.children.size())
        );

        if (custom != null) {
            for (XflInstance instance : custom.children) {
                System.out.println(
                        "    " +
                                instance.libraryItemName
                );
            }
        }

        converter.printSummary();

        converter.printSymbolHierarchy();

        File output =
                new File(
                        root,
                        "peashooter_dragonbones.json"
                );

        converter.exportDragonBones(
                output
        );
    }
    private void buildArmatureHierarchyFallback(
            List<Map<String, Object>> bones,
            List<Map<String, Object>> slots
    ) {

        imageSlotNames.clear();
        slotsByBone.clear();
        imageSlotInstances.clear();
        slotSourceImage.clear();


        // Root bone
        Map<String, Object> rootBone =
                new LinkedHashMap<>();

        rootBone.put("name", "root");
        rootBone.put(
                "transform",
                identityTransform()
        );

        bones.add(rootBone);

        // Find sprites that are children of other sprites.
        Set<String> referencedSprites =
                findReferencedSprites();

        // Add only top-level sprite symbols.
        for (XflSymbol symbol : symbols.values()) {

            if (!isSprite(symbol.libraryName))
                continue;

            if (referencedSprites.contains(
                    symbol.libraryName
            ))
                continue;

            addSymbolToArmature(
                    symbol,
                    "root",
                    new HashSet<>(),
                    bones,
                    slots
            );
        }
    }

    private static final String REFERENCE_ANIMATION = "label/attack";

    private void buildArmatureHierarchy(
            List<Map<String, Object>> bones,
            List<Map<String, Object>> slots
    ) {

        imageSlotNames.clear();
        slotsByBone.clear();
        imageSlotInstances.clear();
        slotSourceImage.clear();

        Map<String, Object> rootBone = new LinkedHashMap<>();
        rootBone.put("name", "root");
        rootBone.put("transform", identityTransform());
        bones.add(rootBone);

        XflSymbol reference = symbols.get(REFERENCE_ANIMATION);

        if (reference == null) {
            System.err.println(
                    "WARNING: reference animation "
                            + REFERENCE_ANIMATION + " not found — "
                            + "falling back to unordered symbol walk."
            );
            buildArmatureHierarchyFallback(bones, slots);
            return;
        }

        List<XflLayer> orderedLayers = new ArrayList<>();

        for (XflTimeline timeline : reference.timelines) {
            orderedLayers.addAll(timeline.layers);
        }

        //Collections.reverse(orderedLayers);

        Set<String> addedInstanceKeys = new LinkedHashSet<>();

        for (XflLayer layer : orderedLayers) {

            XflInstance instance = firstInstanceOf(layer);

            if (instance == null || instance.libraryItemName == null)
                continue;

            String instanceKey =
                    instance.libraryItemName + "@" +
                            (instance.matrix != null
                                    ? Math.round(instance.matrix.tx) + "," +
                                    Math.round(instance.matrix.ty)
                                    : "0,0");

            if (!addedInstanceKeys.add(instanceKey))
                continue;

            XflSymbol symbol = symbols.get(instance.libraryItemName);

            addSymbolToArmature(
                    symbol,
                    "root",
                    new HashSet<>(),
                    bones,
                    slots
            );
        }

        for (XflSymbol animSymbol : symbols.values()) {

            if (!animSymbol.libraryName.startsWith("label/"))
                continue;

            for (XflTimeline timeline : animSymbol.timelines) {
                for (XflLayer layer : timeline.layers) {

                    XflInstance instance = firstInstanceOf(layer);

                    if (instance == null || instance.libraryItemName == null)
                        continue;

                    String instanceKey =
                            instance.libraryItemName + "@" +
                                    (instance.matrix != null
                                            ? Math.round(instance.matrix.tx) + "," +
                                            Math.round(instance.matrix.ty)
                                            : "0,0");

                    if (!addedInstanceKeys.add(instanceKey))
                        continue;

                    System.out.println(
                            "NOTE: " + instance.libraryItemName
                                    + " only found in " + animSymbol.libraryName
                                    + " — appended without a verified z-position."
                    );

                    XflSymbol symbol = symbols.get(instance.libraryItemName);

                    addSymbolToArmature(
                            symbol, "root", new HashSet<>(), bones, slots
                    );
                }
            }
        }
    }

    private XflInstance firstInstanceOf(XflLayer layer) {
        for (XflFrame frame : layer.frames) {
            if (frame.index == 0 && !frame.instances.isEmpty()) {
                return frame.instances.get(0);
            }
        }
        return null;
    }

    // ============================================================
// DRAGONBONES EXPORT
// ============================================================


    private void exportDragonBones(File output)
            throws Exception {

        // --------------------------------------------------------
        // Load rename map + prepare output images folder
        // --------------------------------------------------------

        loadRenameMap(new File(output.getParentFile(), "rename_map.json"));

        File imagesDir = new File(output.getParentFile(), "images");
        imagesDir.mkdirs();

        Map<String, Object> root = new LinkedHashMap<>();

        // --------------------------------------------------------
        // DragonBones metadata
        // --------------------------------------------------------

        root.put("version", "5.5");
        root.put("name", "peashooter");
        root.put("frameRate", 30);

        List<Object> armatureList = new ArrayList<>();

        Map<String, Object> armature =
                new LinkedHashMap<>();

        armature.put("name", "Peashooter");

        List<Map<String, Object>> boneList =
                new ArrayList<>();

        List<Map<String, Object>> slotList =
                new ArrayList<>();

        List<Object> skinList =
                new ArrayList<>();

        // --------------------------------------------------------
        // Convert XFL structural hierarchy into DragonBones
        // --------------------------------------------------------

        buildArmatureHierarchy(
                boneList,
                slotList
        );

        // --------------------------------------------------------
        // Skin
        // --------------------------------------------------------

        Map<String, Object> skin =
                new LinkedHashMap<>();

        skin.put("name", "default");

        List<Object> skinSlots =
                new ArrayList<>();

        for (Map<String, Object> slot :
                slotList) {

            String slotName =
                    (String) slot.get("name");

            String imageName =
                    slotSourceImage.get(slotName);

            if (slotName == null ||
                    imageName == null)
                continue;

            // Resolve the slot's referenced symbol down to an
            // actual leaf image on disk, applying the rename map
            // (or logging it as unmapped) and copying the PNG.
            ResolvedImage resolved =
                    resolveLeafImage(imageSlotInstances.get(slotName));

            String exportName;

            if (resolved != null) {

                copyImage(resolved, imagesDir);

                exportName = resolved.exportName;

            } else {

                System.err.println(
                        "WARNING: could not resolve leaf image for slot "
                                + slotName + " (" + imageName + ")"
                );

                exportName = cleanName(imageName);
            }

            Map<String, Object> skinSlot =
                    new LinkedHashMap<>();

            skinSlot.put(
                    "name",
                    slotName
            );

            List<Object> displays =
                    new ArrayList<>();

            Map<String, Object> display =
                    new LinkedHashMap<>();

            display.put("name", exportName);
            display.put("path", exportName);
            display.put("type", "image");

            XflInstance instance =
                    imageSlotInstances.get(slotName);

            if (resolved != null && resolved.bitmapMatrix != null) {
                display.put(
                        "transform",
                        dragonBonesTransform(resolved.bitmapMatrix)
                );
            }

            displays.add(display);

            skinSlot.put(
                    "display",
                    displays
            );

            skinSlots.add(skinSlot);
        }

        skin.put(
                "slot",
                skinSlots
        );

        skinList.add(skin);

        // --------------------------------------------------------
        // Animations
        // --------------------------------------------------------

        List<Object> animationList =
                new ArrayList<>();

        for (XflSymbol symbol : symbols.values()) {

            if (!symbol.libraryName.startsWith("label/"))
                continue;

            Map<String, Object> animation =
                    convertAnimation(symbol);

            animationList.add(animation);
        }

        armature.put("bone", boneList);
        armature.put("slot", slotList);
        armature.put("skin", skinList);
        armature.put("animation", animationList);

        armatureList.add(armature);

        root.put("armature", armatureList);

        writeJson(output, root);

        // Atlas export removed — you're keeping individual PNGs,
        // not a packed atlas. See copyImage() above instead.

        printUnmappedImagesReport();
    }

    // ============================================================
// XFL MATRIX → DRAGONBONES TRANSFORM
// ============================================================

    private Map<String, Object> dragonBonesTransform(
            XflMatrix matrix
    ) {

        Map<String, Object> transform =
                new LinkedHashMap<>();

        transform.put(
                "x",
                matrix.tx
        );

        transform.put(
                "y",
                matrix.ty
        );

        transform.put(
                "skX",
                matrix.getSkX()
        );

        transform.put(
                "skY",
                matrix.getSkY()

        );

        transform.put(
                "scX",
                matrix.getScaleX()
        );

        transform.put(
                "scY",
                matrix.getScaleY()
        );

        return transform;
    }

    // ============================================================
// XFL TIMELINE → DRAGONBONES ANIMATION
// ============================================================

    private Map<String, Object> convertAnimation(
            XflSymbol symbol,
            XflTimeline timeline
    ) {

        Map<String, Object> animation =
                new LinkedHashMap<>();

        String animationName =
                symbol.libraryName.substring(
                        "label/".length()
                );

        animation.put(
                "name",
                animationName
        );

        int totalFrames = 0;

        for (XflLayer layer :
                timeline.layers) {

            for (XflFrame frame :
                    layer.frames) {

                totalFrames =
                        Math.max(
                                totalFrames,
                                frame.index
                                        + frame.duration
                        );
            }
        }

        animation.put(
                "duration",
                totalFrames
        );

        animation.put(
                "playTimes",
                0
        );

        // DragonBones bone timelines
        Map<String, Object> boneAnimations =
                new LinkedHashMap<>();

        for (XflLayer layer :
                timeline.layers) {

            for (XflFrame frame :
                    layer.frames) {

                for (XflInstance instance :
                        frame.instances) {

                    if (instance.libraryItemName
                            == null)
                        continue;

                    if (!instance.libraryItemName
                            .startsWith("sprite/"))
                        continue;

                    String boneName =
                            instance.libraryItemName
                                    .substring(
                                            "sprite/".length()
                                    );

                    List<Object> frames =
                            (List<Object>)
                                    boneAnimations
                                            .computeIfAbsent(
                                                    boneName,
                                                    k -> new ArrayList<>()
                                            );

                    Map<String, Object> keyframe =
                            new LinkedHashMap<>();

                    keyframe.put(
                            "duration",
                            frame.duration
                    );

                    if (instance.matrix != null) {

                        keyframe.put(
                                "transform",
                                dragonBonesTransform(
                                        instance.matrix
                                )
                        );
                    }

                    frames.add(keyframe);
                }
            }
        }

        // Convert lists into DragonBones bone timeline objects
        List<Object> boneTimelineList =
                new ArrayList<>();

        for (Map.Entry<String, Object> entry :
                boneAnimations.entrySet()) {

            Map<String, Object> boneTimeline =
                    new LinkedHashMap<>();

            boneTimeline.put(
                    "name",
                    entry.getKey()
            );

            boneTimeline.put(
                    "frame",
                    entry.getValue()
            );

            boneTimelineList.add(
                    boneTimeline
            );
        }

        animation.put(
                "bone",
                boneTimelineList
        );

        animation.put(
                "slot",
                createSlotTimelines((XflSymbol) animation)
        );

        return animation;
    }

    // ============================================================
// JSON WRITER
// ============================================================

    private void writeJson(
            File output,
            Object value
    ) throws Exception {

        String json =
                toJson(value, 0);

        java.nio.file.Files.writeString(
                output.toPath(),
                json
        );

        System.out.println(
                "DragonBones JSON written to:"
        );

        System.out.println(
                output.getAbsolutePath()
        );
    }

    private String toJson(
            Object value,
            int indent
    ) {

        if (value == null)
            return "null";

        if (value instanceof String)
            return "\"" +
                    escapeJson((String) value) +
                    "\"";

        if (value instanceof Number)
            return value.toString();

        if (value instanceof Boolean)
            return value.toString();

        if (value instanceof Map) {

            StringBuilder out =
                    new StringBuilder();

            out.append("{\n");

            Map<?, ?> map =
                    (Map<?, ?>) value;

            int i = 0;

            for (Map.Entry<?, ?> entry :
                    map.entrySet()) {

                indent(out, indent + 1);

                out.append("\"")
                        .append(
                                escapeJson(
                                        entry.getKey()
                                                .toString()
                                )
                        )
                        .append("\": ");

                out.append(
                        toJson(
                                entry.getValue(),
                                indent + 1
                        )
                );

                if (i < map.size() - 1)
                    out.append(",");

                out.append("\n");

                i++;
            }

            indent(out, indent);
            out.append("}");

            return out.toString();
        }

        if (value instanceof List) {

            StringBuilder out =
                    new StringBuilder();

            out.append("[\n");

            List<?> list =
                    (List<?>) value;

            for (int i = 0;
                 i < list.size();
                 i++) {

                indent(out, indent + 1);

                out.append(
                        toJson(
                                list.get(i),
                                indent + 1
                        )
                );

                if (i < list.size() - 1)
                    out.append(",");

                out.append("\n");
            }

            indent(out, indent);
            out.append("]");

            return out.toString();
        }

        return "\"" +
                escapeJson(value.toString()) +
                "\"";
    }

    private void indent(
            StringBuilder out,
            int amount
    ) {

        out.append("    ".repeat(amount));
    }

    private String escapeJson(
            String value
    ) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private boolean isSprite(String name) {
        return name != null &&
                name.startsWith("sprite/");
    }

    private boolean isImage(String name) {
        return name != null &&
                name.startsWith("image/");
    }

    private boolean isBitmap(String name) {
        return name != null &&
                name.startsWith("media/");
    }

    private List<XflInstance> getUniqueInstances(
            XflSymbol symbol
    ) {

        Map<String, XflInstance> unique =
                new LinkedHashMap<>();

        for (XflInstance instance :
                symbol.children) {

            if (instance.libraryItemName == null)
                continue;

            /*
             * One symbol can occur many times across its
             * animation frames.
             *
             * For the structural hierarchy we only need
             * the referenced symbol itself once.
             */

            unique.putIfAbsent(
                    instance.libraryItemName,
                    instance
            );
        }

        return new ArrayList<>(
                unique.values()
        );
    }


    private String cleanName(String name) {

        if (name == null)
            return "";

        int slash =
                name.lastIndexOf('/');

        if (slash >= 0)
            name = name.substring(slash + 1);

        return name
                .replace(" ", "_")
                .replace(".", "_");
    }

    private Map<String, Object> identityTransform() {

        Map<String, Object> transform =
                new LinkedHashMap<>();

        transform.put("x", 0.0);
        transform.put("y", 0.0);
        transform.put("skX", 0.0);
        transform.put("skY", 0.0);
        transform.put("scX", 1.0);
        transform.put("scY", 1.0);

        return transform;
    }



    private Map<String, Object> convertAnimation(
            XflSymbol animation
    ) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        String name =
                animation.libraryName.substring(
                        animation.libraryName.lastIndexOf("/") + 1
                );

        result.put("name", name);
        result.put("playTimes", 0);
        result.put(
                "duration",
                getAnimationDuration(animation)
        );

        // --------------------------------------------
        // Bone timelines
        // --------------------------------------------

        Map<String, XflAnimationTrack> tracks =
                collectAnimationTracks(animation);

        List<Object> boneTimelines =
                new ArrayList<>();

        for (XflAnimationTrack track :
                tracks.values()) {

            boneTimelines.add(
                    createBoneTimeline(track)
            );
        }

        result.put(
                "bone",
                boneTimelines
        );

        // --------------------------------------------
        // Slot timelines
        // --------------------------------------------

        List<Object> slotTimelines =
                createSlotTimelines(animation);

        result.put(
                "slot",
                slotTimelines
        );

        return result;
    }

    static class XflAnimatedFrame {

        int index;
        int duration;

        XflMatrix matrix;
        XflColor color;

        String libraryItemName;

        XflSymbol parentSymbol;
        XflInstance sourceInstance;
    }
    private Map<String, XflAnimationTrack>
    collectAnimationTracks(
            XflSymbol animation
    ) {

        Map<String, XflAnimationTrack> tracks =
                new LinkedHashMap<>();

        for (XflTimeline timeline :
                animation.timelines) {

            for (XflLayer layer :
                    timeline.layers) {

                for (XflFrame frame :
                        layer.frames) {

                    for (XflInstance instance :
                            frame.instances) {

                        if (!isSprite(
                                instance.libraryItemName
                        ))
                            continue;



                        String target =
                                instance.libraryItemName +
                                        "#" +
                                        layer.name;

                        XflAnimationTrack track =
                                tracks.computeIfAbsent(
                                        target,
                                        key -> {

                                            XflAnimationTrack t =
                                                    new XflAnimationTrack();

                                            t.target = key;
                                            t.libraryItemName = instance.libraryItemName;  // <-- add this

                                            return t;
                                        }
                                );

                        XflAnimatedFrame animated =
                                new XflAnimatedFrame();

                        animated.index =
                                frame.index;

                        animated.duration =
                                frame.duration;

                        animated.matrix =
                                instance.matrix;

                        animated.color =
                                instance.color;

                        animated.libraryItemName =
                                instance.libraryItemName;

                        animated.sourceInstance = instance;

                        track.frames.add(animated);
                        System.out.println(
                                "    name=" + instance.name +
                                        " library=" + instance.libraryItemName
                        );
                    }
                }
            }
        }

        System.out.println(
                "Animation: " + animation.libraryName
        );

        for (XflAnimationTrack track : tracks.values()) {

            System.out.println(
                    "  Track: " + track.target +
                            " frames=" + track.frames.size()
            );

            int shown = 0;

            for (XflAnimatedFrame frame : track.frames) {

                System.out.println(
                        "    frame=" + frame.index +
                                " duration=" + frame.duration +
                                " matrix=" + (frame.matrix != null)
                                + (frame.matrix != null
                                ? " a=" + frame.matrix.a +
                                " b=" + frame.matrix.b +
                                " c=" + frame.matrix.c +
                                " d=" + frame.matrix.d +
                                " tx=" + frame.matrix.tx +
                                " ty=" + frame.matrix.ty
                                : "")
                );

                if (++shown >= 10)
                    break;
            }
        }

        return tracks;
    }

    private String findSlotForAnimatedInstance(
            XflInstance instance
    ) {

        for (Map.Entry<String, String> entry :
                imageSlotNames.entrySet()) {

            String key = entry.getKey();

            /*
             * Key format:
             *
             * parentBone|libraryItemName|instanceName
             */
            String[] parts =
                    key.split("\\|", -1);

            if (parts.length != 3)
                continue;

            String libraryItem =
                    parts[1];

            String instanceName =
                    parts[2];

            String animatedName =
                    instance.name == null
                            ? ""
                            : instance.name;

            if (libraryItem.equals(
                    instance.libraryItemName
            ) &&
                    instanceName.equals(
                            animatedName
                    )) {

                return entry.getValue();
            }
        }

        return null;
    }

    private List<Object> createSlotTimelines(
            XflSymbol animation
    ) {
        List<Object> timelines =
                new ArrayList<>();

        int duration =
                getAnimationDuration(animation);

        // visibility[slotName][frame] = display index
        // -1 = hidden
        //  0 = visible
        Map<String, List<Integer>> visibility =
                new LinkedHashMap<>();

        // Initialize every structural image slot as hidden.
        for (String slotName :
                imageSlotNames.values()) {

            List<Integer> frames =
                    new ArrayList<>(
                            Collections.nCopies(
                                    duration,
                                    -1
                            )
                    );

            visibility.put(
                    slotName,
                    frames
            );
        }

        // Evaluate the XFL composition at every animation frame.
        for (XflTimeline timeline :
                animation.timelines) {

            for (XflLayer layer :
                    timeline.layers) {

                for (XflFrame frame :
                        layer.frames) {

                    for (XflInstance instance :
                            frame.instances) {

                        if (!isSprite(
                                instance.libraryItemName
                        ))
                            continue;

                        XflSymbol sprite =
                                symbols.get(
                                        instance.libraryItemName
                                );

                        if (sprite == null)
                            continue;

                        markSpriteImagesVisible(
                                instance,
                                visibility,
                                frame.index,
                                frame.duration
                        );
                    }
                }
            }
        }


        // Convert visibility arrays into DragonBones
        // display timelines.
        for (Map.Entry<String, List<Integer>> entry :
                visibility.entrySet()) {

            String slotName =
                    entry.getKey();

            List<Integer> states =
                    entry.getValue();

            Map<String, Object> timeline =
                    new LinkedHashMap<>();

            timeline.put(
                    "name",
                    slotName
            );

            List<Object> frames =
                    new ArrayList<>();

            int currentState =
                    states.isEmpty()
                            ? -1
                            : states.get(0);

            int frameStart = 0;

            for (int frame = 1;
                 frame < states.size();
                 frame++) {

                int state =
                        states.get(frame);

                if (state == currentState)
                    continue;

                Map<String, Object> keyframe =
                        new LinkedHashMap<>();

                keyframe.put(
                        "duration",
                        frame - frameStart
                );

                keyframe.put(
                        "value",
                        currentState
                );

                frames.add(keyframe);

                frameStart = frame;
                currentState = state;
            }

            // Final segment.
            if (!states.isEmpty()) {

                Map<String, Object> keyframe =
                        new LinkedHashMap<>();

                keyframe.put(
                        "duration",
                        states.size() - frameStart
                );

                keyframe.put(
                        "value",
                        currentState
                );

                frames.add(keyframe);
            }

            timeline.put(
                    "displayFrame",
                    frames
            );

            timelines.add(timeline);
        }

        return timelines;
    }
    private void collectSpriteSlots(
            XflSymbol sprite,
            Set<String> usedSlots
    ) {

        if (sprite == null)
            return;

        String boneName =
                cleanName(sprite.libraryName);

        /*
         * ONLY collect image slots directly belonging
         * to this sprite's bone.
         *
         * Do NOT recursively traverse child sprites.
         */
        List<String> slots =
                slotsByBone.get(boneName);

        if (slots == null)
            return;

        usedSlots.addAll(slots);
    }

    private void markSpriteImagesVisible(
            XflInstance instance,
            Map<String, List<Integer>> visibility,
            int globalStart,
            int duration
    ) {
        if (instance == null || !isSprite(instance.libraryItemName))
            return;

        XflSymbol sprite = symbols.get(instance.libraryItemName);

        if (sprite == null)
            return;

        String boneName = cleanName(sprite.libraryName);

        int localStart;
        int localEnd;

        boolean singleFrame =
                "single frame".equalsIgnoreCase(instance.loop);

        if (singleFrame) {
            // Graphic instance pinned to one internal frame —
            // it does NOT advance through its own timeline,
            // regardless of how long it's displayed outside.
            localStart = instance.firstFrame;
            localEnd = localStart + 1;
        } else {
            // "loop" / "play once" / movie clip — original behavior.
            localStart = instance.firstFrame;
            localEnd = localStart + duration;
        }

        for (XflTimeline timeline : sprite.timelines) {
            for (XflLayer layer : timeline.layers) {
                for (XflFrame frame : layer.frames) {

                    int frameStart = frame.index;
                    int frameEnd = frame.index + frame.duration;

                    int overlapStart = Math.max(frameStart, localStart);
                    int overlapEnd = Math.min(frameEnd, localEnd);

                    if (overlapStart >= overlapEnd)
                        continue;

                    // For single-frame mode, the whole outer duration
                    // should be marked visible (it's a static pick),
                    // not just the 1-frame internal overlap window.
                    int overlapGlobalStart = globalStart;
                    int overlapDuration = duration;

                    if (!singleFrame) {
                        overlapGlobalStart =
                                globalStart + (overlapStart - localStart);
                        overlapDuration =
                                overlapEnd - overlapStart;
                    }

                    for (XflInstance child : frame.instances) {

                        if (child.libraryItemName == null)
                            continue;

                        if (isImage(child.libraryItemName)) {

                            String slotName = findSlotName(boneName, child);
                            if (slotName == null) continue;

                            List<Integer> states = visibility.get(slotName);
                            if (states == null) continue;

                            int end = Math.min(
                                    overlapGlobalStart + overlapDuration,
                                    states.size()
                            );

                            for (int i = Math.max(overlapGlobalStart, 0); i < end; i++) {
                                states.set(i, 0);
                            }

                        } else if (isSprite(child.libraryItemName)) {

                            markSpriteImagesVisible(
                                    child,
                                    visibility,
                                    overlapGlobalStart,
                                    overlapDuration
                            );
                        }
                    }
                }
            }
        }
    }

    private Map<String, Object> createBoneTimeline(
            XflAnimationTrack track
    ) {

        Map<String, Object> timeline =
                new LinkedHashMap<>();

        timeline.put(
                "name",
                cleanName(track.libraryItemName)
        );

        List<Object> translateFrames =
                new ArrayList<>();

        List<Object> rotateFrames =
                new ArrayList<>();

        List<Object> scaleFrames =
                new ArrayList<>();

        for (XflAnimatedFrame frame :
                track.frames) {

            if (frame.matrix == null)
                continue;

            XflMatrix m =
                    frame.matrix;

            System.out.println(
                    "DB transform frame=" + frame.index +
                            " det=" + (m.a * m.d - m.b * m.c) +
                            " sx=" + m.getScaleX() +
                            " sy=" + m.getScaleY() +
                            " rot=" + m.getRotationDegrees() +
                            " skew=" + m.getSkewXDegrees()
            );


            // --------------------------------------------
            // Translation
            // --------------------------------------------

            Map<String, Object> translate =
                    new LinkedHashMap<>();

            translate.put(
                    "duration",
                    frame.duration
            );

            translate.put(
                    "position",
                    frame.index
            );

            translate.put(
                    "x",
                    m.tx
            );

            translate.put(
                    "y",
                    m.ty
            );

            translateFrames.add(
                    translate
            );

            // --------------------------------------------
            // Rotation / skew
            // --------------------------------------------

            Map<String, Object> rotate =
                    new LinkedHashMap<>();

            rotate.put(
                    "duration",
                    frame.duration
            );


            rotate.put(
                    "position",
                    frame.index
            );


            rotate.put(
                    "rotate",
                    Math.toRadians(
                            m.getRotationDegrees()
                    )
            );

            rotate.put(
                    "skew",
                    Math.toRadians(
                            m.getSkewXDegrees()
                    )
            );

            rotateFrames.add(
                    rotate
            );

            // --------------------------------------------
            // Scale
            // --------------------------------------------

            Map<String, Object> scale =
                    new LinkedHashMap<>();

            scale.put(
                    "duration",
                    frame.duration
            );

            scale.put(
                    "position",
                    frame.index
            );

            scale.put(
                    "x",
                    m.getScaleX()
            );

            scale.put(
                    "y",
                    m.getScaleY()
            );

            scaleFrames.add(
                    scale
            );
        }

        timeline.put(
                "translateFrame",
                translateFrames
        );

        timeline.put(
                "rotateFrame",
                rotateFrames
        );

        timeline.put(
                "scaleFrame",
                scaleFrames
        );

        return timeline;
    }

    private int getAnimationDuration(
            XflSymbol animation
    ) {

        int duration = 0;

        for (XflTimeline timeline :
                animation.timelines) {

            for (XflLayer layer :
                    timeline.layers) {

                for (XflFrame frame :
                        layer.frames) {

                    duration = Math.max(
                            duration,
                            frame.index +
                                    frame.duration
                    );
                }
            }
        }

        return duration;
    }

    private void printSymbolHierarchy() {

        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "XFL SYMBOL HIERARCHY"
        );
        System.out.println(
                "========================================"
        );

        XflSymbol head =
                symbols.get("sprite/_peashooter_head");

        if (head == null) {
            System.out.println(
                    "sprite/_peashooter_head not found"
            );
            return;
        }

        printChildren(
                head,
                0,
                new HashSet<>()
        );
    }
    private void printChildren(
            XflSymbol symbol,
            int depth,
            Set<String> path
    ) {

        if (symbol == null)
            return;

        if (!path.add(symbol.libraryName))
            return;

        String indent =
                "    ".repeat(depth);

        System.out.println(
                indent + symbol.libraryName
        );

        for (XflInstance instance :
                symbol.children) {

            if (instance.referencedSymbol == null) {

                System.out.println(
                        indent +
                                "    -> " +
                                instance.libraryItemName +
                                " [UNRESOLVED]"
                );

                continue;
            }

            printChildren(
                    instance.referencedSymbol,
                    depth + 1,
                    path
            );
        }

        path.remove(symbol.libraryName);
    }

    static class XflAnimationTrack {

        String target;
        String libraryItemName;

        List<XflAnimatedFrame> frames =
                new ArrayList<>();
    }


    private void buildAllStructuralChildren() {

        for (XflSymbol symbol :
                symbols.values()) {

            if (!symbol.libraryName.startsWith(
                    "sprite/"
            ))
                continue;

            buildStructuralChildren(symbol);
        }
    }

    private void buildStructuralChildren(
            XflSymbol symbol
    ) {

        Map<String, XflInstance> unique =
                new LinkedHashMap<>();

        /*
         * A sprite's timeline describes its composition.
         *
         * We now use the UNION of every frame across the sprite's
         * own timeline, not just frame 0 — some children only
         * appear starting at a later local frame.
         */
        for (XflTimeline timeline :
                symbol.timelines) {

            for (XflLayer layer :
                    timeline.layers) {

                for (XflFrame frame :
                        layer.frames) {

                    for (XflInstance instance :
                            frame.instances) {

                        if (instance.libraryItemName == null)
                            continue;

                        instance.referencedSymbol =
                                symbols.get(
                                        instance.libraryItemName
                                );

                        String key =
                                instance.libraryItemName +
                                        "#" +
                                        layer.name;

                        /*
                         * putIfAbsent keeps the FIRST occurrence
                         * (lowest frame index, since frames are
                         * parsed in document order) as the bind
                         * pose / default-skin transform for this slot.
                         */
                        unique.putIfAbsent(
                                key,
                                instance
                        );
                    }
                }
            }
        }

        symbol.children.clear();

        symbol.children.addAll(
                unique.values()
        );
    }

    private void addSymbolToArmature(
            XflSymbol symbol,
            String parentBone,
            Set<String> recursionStack,
            List<Map<String, Object>> bones,
            List<Map<String, Object>> slots
    ) {

        if (symbol == null)
            return;

        if (!recursionStack.add(
                symbol.libraryName
        ))
            return;

        String boneName =
                cleanName(symbol.libraryName);

        Map<String, Object> bone =
                new LinkedHashMap<>();

        bone.put("name", boneName);

        if (parentBone != null)
            bone.put("parent", parentBone);

        bone.put(
                "transform",
                identityTransform()
        );

        bones.add(bone);

        for (XflInstance child :
                symbol.children) {

            if (isSprite(
                    child.libraryItemName
            )) {

                addSymbolToArmature(
                        symbols.get(
                                child.libraryItemName
                        ),
                        boneName,
                        recursionStack,
                        bones,
                        slots
                );

            } else if (isImage(
                    child.libraryItemName
            )) {

                addImageSlot(
                        boneName,
                        child,
                        slots
                );
            }
        }

        recursionStack.remove(
                symbol.libraryName
        );
    }

    private final Map<String, List<String>> slotsByBone =
            new LinkedHashMap<>();



    private void addImageSlot(
            String parentBone,
            XflInstance instance,
            List<Map<String, Object>> slots
    ) {
        Map<String, Object> slot =
                new LinkedHashMap<>();

        String slotName =
                parentBone +
                        "_slot_" +
                        slots.size();

        imageSlotNames.put(
                getSlotKey(parentBone, instance),
                slotName
        );

        imageSlotInstances.put(
                slotName,
                instance
        );

        slotSourceImage.put(
                slotName,
                instance.libraryItemName
        );

        slot.put("name", slotName);
        slot.put("parent", parentBone);
        slot.put("displayIndex", 0);

        if (instance.matrix != null) {
            slot.put(
                    "transform",
                    dragonBonesTransform(instance.matrix)
            );
        }

        slotsByBone
                .computeIfAbsent(
                        parentBone,
                        key -> new ArrayList<>()
                )
                .add(slotName);

        slots.add(slot);
    }






    private String getSlotKey(
            String parentBone,
            XflInstance instance
    ) {

        return parentBone +
                "|" +
                instance.libraryItemName +
                "|" +
                (instance.name == null
                        ? ""
                        : instance.name);
    }



    private String getInstanceKey(
            XflInstance instance
    ) {
        return instance.libraryItemName +
                "|" +
                (instance.name == null
                        ? ""
                        : instance.name);
    }

    private String findSlotName(
            String parentBone,
            XflInstance instance
    ) {
        return imageSlotNames.get(
                getSlotKey(parentBone, instance)
        );
    }

    private Set<String> findReferencedSprites() {

        Set<String> referenced =
                new HashSet<>();

        for (XflSymbol symbol :
                symbols.values()) {

            if (!isSprite(
                    symbol.libraryName
            ))
                continue;

            for (XflInstance child :
                    symbol.children) {

                if (isSprite(
                        child.libraryItemName
                )) {

                    referenced.add(
                            child.libraryItemName
                    );
                }
            }
        }

        return referenced;
    }

    private Set<String> collectAnimationAssets(
            XflSymbol animation
    ) {

        Set<String> assets =
                new LinkedHashSet<>();

        for (XflTimeline timeline :
                animation.timelines) {

            for (XflLayer layer :
                    timeline.layers) {

                for (XflFrame frame :
                        layer.frames) {

                    for (XflInstance instance :
                            frame.instances) {

                        if (isImage(
                                instance.libraryItemName
                        )) {

                            assets.add(
                                    instance.libraryItemName
                            );

                        } else if (isSprite(
                                instance.libraryItemName
                        )) {

                            collectSpriteAssets(
                                    symbols.get(
                                            instance.libraryItemName
                                    ),
                                    assets
                            );
                        }
                    }
                }
            }
        }

        return assets;
    }

    private void collectSpriteAssets(
            XflSymbol sprite,
            Set<String> assets
    ) {

        if (sprite == null)
            return;

        for (XflInstance child :
                sprite.children) {

            if (isImage(
                    child.libraryItemName
            )) {

                assets.add(
                        child.libraryItemName
                );

            } else if (isSprite(
                    child.libraryItemName
            )) {

                collectSpriteAssets(
                        symbols.get(
                                child.libraryItemName
                        ),
                        assets
                );
            }
        }
    }


    private void collectAnimationVisibility(
            XflSymbol symbol,
            int frameIndex,
            Map<String, List<Integer>> visibility,
            String parentBone
    ) {

        if (symbol == null)
            return;

        String boneName =
                cleanName(symbol.libraryName);

        /*
         * Walk the structural children of this symbol.
         */
        for (XflInstance child :
                symbol.children) {

            if (isSprite(child.libraryItemName)) {

                XflSymbol childSymbol =
                        symbols.get(
                                child.libraryItemName
                        );

                collectAnimationVisibility(
                        childSymbol,
                        frameIndex,
                        visibility,
                        boneName
                );

            } else if (isImage(
                    child.libraryItemName
            )) {

                String slotName =
                        imageSlotNames.get(
                                getSlotKey(
                                        boneName,
                                        child
                                )
                        );

                if (slotName == null)
                    continue;

                List<Integer> frames =
                        visibility.get(slotName);

                if (frames == null)
                    return;

                if (frameIndex >= 0 &&
                        frameIndex < frames.size()) {

                    frames.set(
                            frameIndex,
                            0
                    );
                }
            }
        }
    }


    // ============================================================
// RESOLVE SYMBOL REFERENCES
// ============================================================

    private void resolveReferences() {

        for (XflSymbol symbol : symbols.values()) {

            for (XflTimeline timeline :
                    symbol.timelines) {

                for (XflLayer layer :
                        timeline.layers) {

                    for (XflFrame frame :
                            layer.frames) {

                        for (XflInstance instance :
                                frame.instances) {

                            resolveInstance(instance);
                        }
                    }
                }
            }
        }
    }

    static class ResolvedImage {
        File sourceFile;
        String exportName;
        XflMatrix bitmapMatrix;   // <-- add this
    }

    private void resolveInstance(
            XflInstance instance
    ) {

        if (instance.libraryItemName == null ||
                instance.libraryItemName.isEmpty()) {

            return;
        }

        XflSymbol referenced =
                symbols.get(instance.libraryItemName);

        if (referenced == null) {

            System.err.println(
                    "WARNING: unresolved reference: "
                            + instance.libraryItemName
            );

            return;
        }

        instance.referencedSymbol =
                referenced;
    }

    private void exportTextureAtlas(File outputDir)
            throws Exception {

        File mediaDir =
                new File(
                        xflRoot,
                        "library/media"
                );

        if (!mediaDir.exists()) {
            System.out.println(
                    "No media directory: " +
                            mediaDir.getAbsolutePath()
            );
            return;
        }

        File[] files =
                mediaDir.listFiles(
                        (dir, name) ->
                                name.toLowerCase()
                                        .endsWith(".png")
                );

        if (files == null || files.length == 0) {
            System.out.println("No PNG assets found.");
            return;
        }

        Arrays.sort(
                files,
                Comparator.comparing(File::getName)
        );

        // --------------------------------------------------------
        // Atlas settings
        // --------------------------------------------------------

        int padding = 2;
        int columns = 8;

        int cellWidth = 128;
        int cellHeight = 128;

        int rows =
                (int) Math.ceil(
                        files.length / (double) columns
                );

        int atlasWidth =
                columns * cellWidth;

        int atlasHeight =
                rows * cellHeight;

        BufferedImage atlas =
                new BufferedImage(
                        atlasWidth,
                        atlasHeight,
                        BufferedImage.TYPE_INT_ARGB
                );

        Graphics2D graphics =
                atlas.createGraphics();

        List<Map<String, Object>> regions =
                new ArrayList<>();

        // --------------------------------------------------------
        // Pack images
        // --------------------------------------------------------

        for (int i = 0; i < files.length; i++) {

            File imageFile = files[i];

            BufferedImage image =
                    ImageIO.read(imageFile);

            if (image == null)
                continue;

            int column =
                    i % columns;

            int row =
                    i / columns;

            int x =
                    column * cellWidth +
                            padding;

            int y =
                    row * cellHeight +
                            padding;

            graphics.drawImage(
                    image,
                    x,
                    y,
                    null
            );

            Map<String, Object> region =
                    new LinkedHashMap<>();

            String name =
                    imageFile.getName();

            if (name.toLowerCase()
                    .endsWith(".png")) {

                name =
                        name.substring(
                                0,
                                name.length() - 4
                        );
            }


            region.put("name", name);
            region.put("x", x);
            region.put("y", y);
            region.put("width", image.getWidth());
            region.put("height", image.getHeight());

            regions.add(region);
        }

        graphics.dispose();

        // --------------------------------------------------------
        // Write atlas PNG
        // --------------------------------------------------------

        File atlasImage =
                new File(
                        outputDir,
                        "peashooter_atlas.png"
                );

        ImageIO.write(
                atlas,
                "png",
                atlasImage
        );

        // --------------------------------------------------------
        // Write atlas JSON
        // --------------------------------------------------------

        Map<String, Object> atlasJson =
                new LinkedHashMap<>();

        atlasJson.put(
                "name",
                "peashooter_atlas"
        );

        atlasJson.put(
                "imagePath",
                "peashooter_atlas.png"
        );

        atlasJson.put(
                "width",
                atlasWidth
        );

        atlasJson.put(
                "height",
                atlasHeight
        );

        atlasJson.put(
                "SubTexture",
                regions
        );

        File atlasJsonFile =
                new File(
                        outputDir,
                        "peashooter_atlas.json"
                );

        writeJson(
                atlasJsonFile,
                atlasJson
        );

        System.out.println(
                "Atlas created: " +
                        atlasImage.getAbsolutePath()
        );

        System.out.println(
                "Atlas JSON created: " +
                        atlasJsonFile.getAbsolutePath()
        );
    }


    // ============================================================
// RENAME MAP
// ============================================================

    private Map<String, String> renameMap = new LinkedHashMap<>();
    private final Set<String> unmappedImages = new LinkedHashSet<>();

    private void loadRenameMap(File jsonFile) throws Exception {
        if (!jsonFile.exists()) {
            System.out.println("No rename map found at " + jsonFile
                    + " — all images will use their raw library names.");
            return;
        }

        String text = java.nio.file.Files.readString(jsonFile.toPath());

        // Minimal hand-rolled parse: this is a flat {"key": "value", ...}
        // object, so a tiny regex-based reader is fine — no need to pull
        // in a JSON library for this one shape.
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile(
                        "\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\""
                ).matcher(text);

        while (m.find()) {
            renameMap.put(m.group(1), m.group(2));
        }

        System.out.println("Loaded " + renameMap.size() + " rename entries.");
    }

// ============================================================
// LEAF IMAGE RESOLUTION
// ============================================================

    /**
     * Given any instance (sprite or image), recursively descend
     * through single-frame wrapper sprites until an actual
     * image/... reference is found, and resolve it to:
     *   - the real File on disk
     *   - the mapped display name (or a fallback + warning)
     *
     * This does NOT walk animated multi-frame composition — it's
     * meant to be called once per resolved leaf slot (i.e. after
     * the frame-resolver has already decided this instance is the
     * one showing at a given frame), to find out what file backs it.
     */
    private ResolvedImage resolveLeafImage(XflInstance instance) {

        if (instance == null || instance.libraryItemName == null)
            return null;

        String libraryItemName = instance.libraryItemName;

        if (isBitmap(libraryItemName)) {

            File source = libraryFiles.get(libraryItemName);

            if (source == null) {
                System.err.println("WARNING: no file on disk for " + libraryItemName);
                return null;
            }

            String bareKey = libraryItemName.startsWith("media/")
                    ? libraryItemName.substring("media/".length())
                    : libraryItemName;

            String mapped = renameMap.get(bareKey);

            if (mapped == null) {
                mapped = cleanName(libraryItemName);
                unmappedImages.add(bareKey);
            }

            ResolvedImage result = new ResolvedImage();
            result.sourceFile = source;
            result.exportName = mapped;
            result.bitmapMatrix = instance.matrix;   // <-- captured here
            return result;
        }

        if (isSprite(libraryItemName) || isImage(libraryItemName)) {

            XflSymbol symbol = symbols.get(libraryItemName);

            if (symbol == null)
                return null;

            for (XflTimeline timeline : symbol.timelines) {
                for (XflLayer layer : timeline.layers) {
                    for (XflFrame frame : layer.frames) {
                        for (XflInstance child : frame.instances) {
                            ResolvedImage found = resolveLeafImage(child);
                            if (found != null)
                                return found;
                        }
                    }
                }
            }
        }

        return null;
    }
// ============================================================
// COPY PASS
// ============================================================

    private final Set<String> alreadyCopiedSources = new HashSet<>();
    private final Map<String, String> exportNameToSourcePath = new LinkedHashMap<>();

    private void copyImage(ResolvedImage image, File outputDir) throws Exception {

        if (image == null)
            return;

        String sourceKey = image.sourceFile.getAbsolutePath();

        if (!alreadyCopiedSources.add(sourceKey))
            return; // already copied this run

        // Collision check: two DIFFERENT source files resolving to
        // the same export name would silently overwrite one another.
        String existingSource = exportNameToSourcePath.get(image.exportName);

        if (existingSource != null && !existingSource.equals(sourceKey)) {
            throw new RuntimeException(
                    "Rename collision: \"" + image.exportName
                            + "\" is claimed by both:\n  " + existingSource
                            + "\n  " + sourceKey
                            + "\nFix the rename map before continuing."
            );
        }

        exportNameToSourcePath.put(image.exportName, sourceKey);

        File dest = new File(outputDir, image.exportName + ".png");

        java.nio.file.Files.copy(
                image.sourceFile.toPath(),
                dest.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );
    }

    private void printUnmappedImagesReport() {
        if (unmappedImages.isEmpty())
            return;

        System.out.println();
        System.out.println("========================================");
        System.out.println("UNMAPPED IMAGES (using fallback names)");
        System.out.println("========================================");
        for (String name : unmappedImages) {
            System.out.println("  \"" + name + "\": \"" + cleanName(name) + "\",");
        }
        System.out.println("Add these to your rename map with proper names.");
    }


}