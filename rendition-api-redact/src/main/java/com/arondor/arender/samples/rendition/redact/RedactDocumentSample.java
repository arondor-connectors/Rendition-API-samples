package com.arondor.arender.samples.rendition.redact;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;

import com.arender.rendition.client.RenditionRestClient;
import com.arondor.viewer.annotation.api.Annotation;
import com.arondor.viewer.annotation.api.RedactTextElemType;
import com.arondor.viewer.annotation.common.AnnotationId;
import com.arondor.viewer.annotation.common.Color;
import com.arondor.viewer.client.api.document.DocumentContainer;
import com.arondor.viewer.client.api.document.DocumentId;
import com.arondor.viewer.client.api.document.DocumentLayout;
import com.arondor.viewer.client.api.document.DocumentPageLayout;
import com.arondor.viewer.client.api.document.DocumentReference;
import com.arondor.viewer.client.api.document.PositionText;
import com.arondor.viewer.client.api.document.TextRange;
import com.arondor.viewer.client.api.document.VideoLayout;
import com.arondor.viewer.client.api.document.altercontent.AlterContentDescriptionWithAnnotations;
import com.arondor.viewer.client.api.geometry.PageRelativePosition;
import com.arondor.viewer.client.api.geometry.PageRelativePositionList;
import com.arondor.viewer.client.api.search.PageSearchResult;
import com.arondor.viewer.client.api.search.PageSearchResult.PageSearchResultItem;
import com.arondor.viewer.client.api.search.SearchOptions;
import com.arondor.viewer.common.client.util.geometry.PositionTextUtil;
import com.arondor.viewer.rendition.api.document.DocumentAccessor;
import com.arondor.viewer.rendition.api.document.DocumentAccessorSelector;

public class RedactDocumentSample
{
    private static RenditionRestClient renditionRestClient = new RenditionRestClient();

    // Text to redact
    private static String searchText = "[0-9][0-9][0-9]-[0-9][0-9][0-9]-[0-9][0-9][0-9]";

    // Path of the document to redact
    private static String sourceFileName = "samples/multidocs-redact.zip";

    // Rendition host
    private static String renditionHost = "http://localhost:8761/";

    public static void main(String[] args) throws Exception
    {
        renditionRestClient.setAddress(renditionHost);

        File sourceFile = new File(sourceFileName);
        FileInputStream fileInputStream = new FileInputStream(sourceFile);
        DocumentId documentId = new DocumentId(UUID.randomUUID().toString());

        renditionRestClient.uploadDocument(documentId, "application/pdf", sourceFile.getName(), fileInputStream);

        DocumentLayout documentLayout = renditionRestClient.getDocumentLayout(documentId);

        recursiveSearchAndRedact(documentLayout);

        // Evict the source document from the rendition
        renditionRestClient.evict(documentId);
    }

    public static void recursiveSearchAndRedact(DocumentLayout documentLayout) throws Exception
    {
        if (documentLayout instanceof DocumentContainer)
        {
            DocumentContainer documentContainer = (DocumentContainer) documentLayout;
            for (DocumentLayout childLayout : documentContainer.getChildren())
            {
                recursiveSearchAndRedact(childLayout);
            }
        }
        else if (documentLayout instanceof DocumentReference)
        {

            DocumentLayout referencedDocumentLayout = renditionRestClient
                    .getDocumentLayout(documentLayout.getDocumentId());
            recursiveSearchAndRedact(referencedDocumentLayout);
        }
        else if (documentLayout instanceof VideoLayout)
        {
            // skip videos
        }
        else if (documentLayout instanceof DocumentPageLayout)
        {
            searchAndRedactDocument((DocumentPageLayout) documentLayout);
        }
    }

    public static void searchAndRedactDocument(DocumentPageLayout documentPageLayout) throws Exception
    {
        DocumentId documentId = documentPageLayout.getDocumentId();
        List<Annotation> annotationList = new ArrayList<Annotation>();
        boolean caseSensitive = false;
        boolean accentSensitive = false;
        boolean regex = true;
        SearchOptions searchOptions = new SearchOptions(searchText, caseSensitive, accentSensitive, regex, null, null);
        for (int pageNumber = 0; pageNumber < documentPageLayout.getPageCount(); pageNumber++)
        {
            PageSearchResult pageSearchResult = renditionRestClient.getAdvancedSearchPageResult(documentId,
                    searchOptions, pageNumber);
            buildRedactAnnotations(annotationList, pageSearchResult, pageNumber);
        }
        AlterContentDescriptionWithAnnotations alterContentDescription = new AlterContentDescriptionWithAnnotations();
        alterContentDescription.setOperationName("renderAnnotations");
        alterContentDescription.setAnnotations(annotationList);
        List<DocumentId> sourceDocumentIdList = new ArrayList<>();
        sourceDocumentIdList.add(documentId);
        DocumentId alteredDocumentId = renditionRestClient.alterDocumentContent(sourceDocumentIdList,
                alterContentDescription);
        DocumentAccessor alterDocumentAccessor = renditionRestClient.getDocumentAccessor(alteredDocumentId,
                DocumentAccessorSelector.INITIAL);

        String targetFileName = "redacted-" + documentPageLayout.getDocumentTitle() + ".pdf";
        File targetFile = new File(targetFileName);
        FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
        IOUtils.copy(alterDocumentAccessor.getInputStream(), fileOutputStream);

        // Evict the redacted document from the rendition
        renditionRestClient.evict(alteredDocumentId);
    }

    public static void buildRedactAnnotations(List<Annotation> annotationList, PageSearchResult pageSearchResult,
            int pageNumber)
    {
        for (PageSearchResultItem pageSearchResultItem : pageSearchResult.getSearchResults())
        {
            PositionText positionText = pageSearchResultItem.getPositionText();
            for (TextRange textRange : pageSearchResultItem.getTextRangeList())
            {
                PageRelativePosition pageRelativePosition = PositionTextUtil.computeTextRangeCoordinates(positionText,
                        textRange);
                RedactTextElemType redactTextElemType = new RedactTextElemType();
                redactTextElemType.setId(new AnnotationId(UUID.randomUUID().toString()));
                redactTextElemType.setColor(new Color(0, 0, 0));
                redactTextElemType.setInteriorColor(new Color(0, 0, 0));
                redactTextElemType.setPage(pageNumber);
                redactTextElemType.setPosition(pageRelativePosition);
                PageRelativePositionList pageRelativePositionList = new PageRelativePositionList();
                pageRelativePositionList.add(pageRelativePosition);
                redactTextElemType.setCoords(pageRelativePositionList);
                redactTextElemType.setCreator("admin");
                redactTextElemType.setCreationDate(new Date());
                redactTextElemType.setOpacity(1f);
                annotationList.add(redactTextElemType);
            }
        }
    }
}
