package viccrubs.appautotesting.models;

import io.appium.java_client.AppiumDriver;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;
import lombok.var;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import viccrubs.appautotesting.config.Config;
import viccrubs.appautotesting.log.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Ui implements Logger {

    private @Getter final String activityName;

    private @Getter String xmlSource;

    private @Getter String currentPackage;

    // xml source may change automatically
    // use more stable classname hierarchy as signature
    private @Getter String signature = "";

    private @Getter Document xmlDocument;

    private @Getter List<UiElement> leafElements;

    public boolean completed() {
        return leafElements.stream().allMatch(UiElement::isAccessed);
    }

    public static Ui create(AppiumDriver driver) {
        return new Ui(driver.currentActivity(), driver.getPageSource());
    }

    public Ui(String activityName, String xmlSource) {
        this.activityName = activityName;
        this.xmlSource = xmlSource;

        // initialize
        initialize();
    }

    public UiElement getNextUnaccessedElement() {
        for (val element: leafElements) {
            if (!element.isAccessed()) {
                return element;
            }
        }
        return null;
    }

    public Optional<UiElement> findConfigElement(Config.Element configElement) {
        return leafElements.stream().filter(x -> x.matchConfigElement(configElement)).findFirst();
    }

    @Override
    public String toString() {
        return String.format("code: %d, activity: %s", hashCode(), activityName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ui)) return false;
        Ui ui = (Ui) o;
        return getSignature().equals(ui.getSignature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSignature());
    }

    // fill signature and leaf elements
    @SneakyThrows
    private void initialize() {
        // https://stackoverflow.com/a/11264294/2725415
        xmlSource = xmlSource.replaceFirst("<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>", "<?xml version=\"1.1\" encoding=\"UTF-8\"?>");

        leafElements = new ArrayList<>();

        var dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbFactory.newDocumentBuilder();
        xmlDocument = builder.parse(new InputSource(new StringReader(xmlSource)));

        Node root = xmlDocument.getFirstChild().getFirstChild(); // hierarchy is the first child

        var rootElement = new UiElement(new UiHierarchy(), root.getNodeName(), 1, this, root);

        // set package
        this.currentPackage = rootElement.getPackage();

        // dfs scan all leaf elements
        initializeRec(root, rootElement, leafElements);
    }

    private void initializeRec(Node root, UiElement rootElement, List<UiElement> result) {

        // ignore specified elements
        if (Config.IGNORED_ELEMENTS.stream().anyMatch(rootElement::matchConfigElement)) {
            return;
        }

        // append signature
        signature += rootElement.getTagName() + ";";

        // get all children of element
        NodeList children = root.getChildNodes();

        if (children.getLength() == 0) {
            // is a leaf elements, add to list
            result.add(rootElement);
        } else {
            // is not, continue recurse

            // generate all UIElement
            val childrenUiElements = new ArrayList<UiElement>();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                UiElement lastSameElement = null;
                for (int j = childrenUiElements.size() - 1; j >= 0; j--) {
                    if (childrenUiElements.get(j).getTagName().equals(child.getNodeName())) {
                        lastSameElement = childrenUiElements.get(j);
                        break;
                    }
                }

                childrenUiElements.add(
                    new UiElement(rootElement.getHierarchy(), child.getNodeName(),
                        lastSameElement == null ? 1 : lastSameElement.getIndex() + 1,
                        rootElement.getUi(),
                        child
                    ));
            }

            for (int i = 0; i < children.getLength(); i++) {
                initializeRec(children.item(i), childrenUiElements.get(i), result);

            }
        }
    }


}
