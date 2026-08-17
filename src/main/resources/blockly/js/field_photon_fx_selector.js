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

    getText_() {
        let text = this.readableName || FieldPhotonFXSelector.getDefaultText();

        if (text.length > FieldPhotonFXSelector.MAX_DISPLAY_LENGTH) {
            text = text.substring(0, FieldPhotonFXSelector.MAX_DISPLAY_LENGTH - 3) + '...';
        }
        return text + FieldPhotonFXSelector.DROPDOWN_ARROW;
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

    static isPhotonFxAvailable_() {
        return typeof photon_fx !== 'undefined'
            && typeof photon_fx.openDataListEntrySelector === 'function';
    }

    logMissingPhotonFx_() {
        try {
            const isTop = window === window.top;
            const topHas = window.top ? typeof window.top.photon_fx !== 'undefined' : 'without window.top';
            const parentHas = window.parent ? typeof window.parent.photon_fx !== 'undefined' : 'without window.parent';
            console.error(
                '[Photon][diag] window===window.top: ' + isTop
                + ' | window.top.photon_fx exists: ' + topHas
                + ' | window.parent.photon_fx exists: ' + parentHas
                + ' | href: ' + window.location.href
            );
        } catch (diagErr) {
            console.error('[Photon][diag] error collecting diagnostics: ' + diagErr);
        }
        console.error('[Photon] photon_fx.openDataListEntrySelector not found');
    }

    showEditor_(e) {
        const currentTime = Date.now();
        const isDoubleClick = currentTime - this.lastClickTime < FieldPhotonFXSelector.DOUBLE_CLICK_TIME_MS;

        if (!isDoubleClick) {
            this.lastClickTime = currentTime;
            return;
        }

        this.lastClickTime = 0;
        if (e) e.stopPropagation();

        if (!FieldPhotonFXSelector.isPhotonFxAvailable_()) {
            this.logMissingPhotonFx_();
            return;
        }

        try {
            photon_fx.openDataListEntrySelector({
                callback: (value, readableName) => this.handleEntrySelected_(value, readableName)
            });
        } catch (err) {
            console.error('[Photon] error opening entry selector: ' + err);
        }
    }

    handleEntrySelected_(value, readableName) {
        const safeValue = value != null ? String(value).trim() : '';
        this.cachedReadableName = readableName ? String(readableName).trim() : safeValue;

        const previousGroup = Blockly.Events.getGroup();
        Blockly.Events.setGroup(true);

        try {
            this.setValue(safeValue);
        } finally {
            Blockly.Events.setGroup(previousGroup);
            if (typeof javabridge !== 'undefined') {
                javabridge.triggerEvent?.();
            }
        }
    }

    doValueUpdate_(newValue) {
        if (newValue !== this.value_) {
            this.updateReadableName_(newValue);
        }
        super.doValueUpdate_(newValue);
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
        fieldElement.textContent = this.getValue() ?? '';
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

try {
    Blockly.fieldRegistry.register('field_photon_fx_selector', FieldPhotonFXSelector);
} catch (err) {
    console.warn('[Photon] field_photon_fx_selector already registered, ignoring: ' + err);
}