# NiFi OpenOCR Processor

This NiFi processor uses an external [OpenOCR](https://github.com/tleyden/open-ocr) stack to extract text from content.

Extracted text is passed to the `extracted` relation, and the original text is forwarded to either the `success` or
`failure` relations depending on whether the OCR was performed successfully or not.

## Building

This processor can be built by calling

    mvn clean package

in the top level directory. The resultant NAR file will be saved in `openocr-nar/target`.

## Tutorial

For more information, refer to the `tutorial/` directory for a walkthrough of how to build, install and use the processor.