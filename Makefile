PROJECT_NAME := Gypsum
SDK_TARGET := android-19

gypsum: project.properties
	ant -e debug
	for i in Gypsum-debug.apk Gypsum.apk; do ln -sf ../out/gypsum-build/$$i ../out/$$i; done

clean: project.properties
	ant -e clean

test: 
	$(warning Not implemented)

project.properties:
	android update project -p . -n $(PROJECT_NAME) -t $(SDK_TARGET)

.PHONY: gypsum
