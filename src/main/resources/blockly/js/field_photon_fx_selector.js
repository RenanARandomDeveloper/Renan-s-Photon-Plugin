class FieldPhotonFXSelector extends Blockly.Field {
    EDITABLE = true;
    SERIALIZABLE = true;
    CURSOR = 'pointer';

    static DROPDOWN_ARROW = ' \u25BE';
    static DOUBLE_CLICK_TIME_MS = 500;
    static MAX_DISPLAY_LENGTH = 75;

    constructor(opt_validator, opt_config) {
        const initialValue = opt_config?.value ?? '';
        super(initialValue, opt_validator, opt_config);

        this.lastClickTime = 0;
        this.readableName = null;
        this.cachedReadableName = null;

        if (opt_config) {
            this.configure_(opt_config);
        }
        this.setTooltip(() => this.getTooltipText());
    }

    getTooltipText() {
        if (this.getValue()) {
            return this.readableName || this.getValue();
        }
        return typeof javabridge !== 'undefined'
            ? javabridge.t('blockly.field_data_list_selector.tooltip.empty')
            : 'No entry selected';
    }

    static getDefaultText() {
        return typeof javabridge !== 'undefined'
            ? javabridge.t('blockly.extension.data_list_selector.no_entry')
            : 'None';
    }

    static fromJson(options) {
        return new this(undefined, options);
    }

    doClassValidation_(newValue) {
        if (newValue === null || newValue === undefined) {
            return null;
        }
        return String(newValue);
    }

    showEditor_(e) {
        const currentTime = Date.now();
        if (currentTime - this.lastClickTime < FieldPhotonFXSelector.DOUBLE_CLICK_TIME_MS) {
            if (e) e.stopPropagation();

            if (typeof photon_fx === 'undefined' || typeof photon_fx.openDataListEntrySelector !== 'function') {
                try {
                    const isTop = (window === window.top);
                    const topHas = window.top ? (typeof window.top.photon_fx !== 'undefined') : 'sem window.top';
                    const parentHas = window.parent ? (typeof window.parent.photon_fx !== 'undefined') : 'sem window.parent';
                    console.error("[Photon][diag] window===window.top: " + isTop
                        + " | window.top.photon_fx existe: " + topHas
                        + " | window.parent.photon_fx existe: " + parentHas
                        + " | href: " + window.location.href);
                } catch (diagErr) {
                    console.error("[Photon][diag] erro ao coletar diagnóstico: " + diagErr);
                }

                console.error("[Photon] photon_fx.openDataListEntrySelector not found");
                return;
            }

            photon_fx.openDataListEntrySelector({
                'callback': (value, readableName) => {
                    const safeValue = String(value || '').trim();
                    this.cachedReadableName = readableName ? String(readableName).trim() : safeValue;

                    const group = Blockly.Events.getGroup();
                    Blockly.Events.setGroup(true);

                    try {
                        this.setValue(safeValue);
                    } finally {
                        Blockly.Events.setGroup(group);
                        if (typeof javabridge !== 'undefined') {
                            javabridge.triggerEvent?.();
                        }
                    }
                }
            });

        } else {
            this.lastClickTime = currentTime;
        }
    }

    doValueUpdate_(newValue) {
        if (newValue !== this.value_) {
            this.updateReadableName_(newValue);
        }
        super.doValueUpdate_(newValue);
    }

    getText_() {
        let text = this.readableName || FieldPhotonFXSelector.getDefaultText();

        if (text.length > FieldPhotonFXSelector.MAX_DISPLAY_LENGTH) {
            text = text.substring(0, FieldPhotonFXSelector.MAX_DISPLAY_LENGTH - 3) + '...';
        }
        return text + FieldPhotonFXSelector.DROPDOWN_ARROW;
    }

    updateReadableName_(value) {
        if (this.cachedReadableName) {
            this.readableName = this.cachedReadableName;
            this.cachedReadableName = null;
        } else if (value) {
            this.readableName = value;
        } else {
            this.readableName = FieldPhotonFXSelector.getDefaultText();
        }
    }

    toXml(fieldElement) {
        fieldElement.textContent = this.getValue();
        if (this.readableName) {
            fieldElement.setAttribute('readable_name', this.readableName);
        }
        return fieldElement;
    }

    fromXml(fieldElement) {
        const savedReadableName = fieldElement.getAttribute('readable_name');
        if (savedReadableName) {
            const trimmed = String(savedReadableName).trim();
            if (trimmed) this.cachedReadableName = trimmed;
        }
        this.setValue(fieldElement.textContent);
    }

    dispose() {
        super.dispose();
        this.cachedReadableName = null;
        this.readableName = null;
    }
}

Blockly.fieldRegistry.register('field_photon_fx_selector', FieldPhotonFXSelector);