<!--
   The MIT License
   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <div>
    <div class="wizard-info-block">
      <div>
        {{ $t('wizard.member.info1') }}
        <br />
        <br />
        {{ $t('wizard.member.info2') }}
      </div>
      <div class="action-block">
        <xrd-button
          outlined
          data-test="select-client-button"
          @click="showSelectClient = true"
          >{{ $t('wizard.member.select') }}</xrd-button
        >
      </div>
    </div>

    <ValidationObserver ref="form2" v-slot="{ invalid }">
      <div class="wizard-step-form-content">
        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberName')"
            :help-text="$t('wizard.client.memberNameTooltip')"
          />
          <div data-test="selected-member-name">{{ selectedMemberName }}</div>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberClass')"
            :help-text="$t('wizard.client.memberClassTooltip')"
          />

          <ValidationProvider
            v-slot="{}"
            name="addClient.memberClass"
            rules="required"
          >
            <v-select
              v-model="memberClass"
              :items="memberClassesCurrentInstance"
              class="wizard-form-input"
              data-test="member-class-input"
              :placeholder="$t('wizard.selectMemberClass')"
              outlined
            ></v-select>
          </ValidationProvider>
        </div>
        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberCode')"
            :help-text="$t('wizard.client.memberCodeTooltip')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            name="addClient.memberCode"
            rules="required|xrdIdentifier"
          >
            <v-text-field
              v-model="memberCode"
              class="wizard-form-input"
              type="text"
              :error-messages="errors"
              :placeholder="$t('wizard.memberCode')"
              autofocus
              outlined
              data-test="member-code-input"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div v-if="duplicateClient" class="wizard-duplicate-warning">
          {{ $t('wizard.client.memberExists') }}
        </div>
      </div>
      <div class="button-footer">
        <div class="button-group">
          <xrd-button outlined data-test="cancel-button" @click="cancel">{{
            $t('action.cancel')
          }}</xrd-button>
        </div>
        <xrd-button
          :disabled="invalid || duplicateClient || checkRunning"
          data-test="next-button"
          @click="done"
          >{{ $t('action.next') }}</xrd-button
        >
      </div>
    </ValidationObserver>

    <SelectClientDialog
      :title="$t('wizard.addMemberTitle')"
      :search-label="$t('wizard.member.searchLabel')"
      :dialog="showSelectClient"
      :selectable-clients="selectableMembers"
      @cancel="showSelectClient = false"
      @save="saveSelectedClient"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { Client } from '@/openapi-types';
import { debounce, isEmpty } from '@/util/helpers';
import { ValidationObserver, ValidationProvider } from 'vee-validate';
import { AddMemberWizardModes } from '@/global';
import { validate } from 'vee-validate';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default Vue.extend({
  components: {
    ValidationObserver,
    ValidationProvider,
    SelectClientDialog,
  },
  data() {
    return {
      showSelectClient: false as boolean,
      checkRunning: false as boolean,
      isMemberCodeValid: true,
    };
  },
  computed: {
    ...mapGetters([
      'reservedMember',
      'memberClassesCurrentInstance',
      'selectedMemberName',
      'currentSecurityServer',
    ]),

    memberClass: {
      get(): string {
        return this.$store.getters.memberClass;
      },
      set(value: string) {
        this.$store.commit('setMemberClass', value);
      },
    },

    memberCode: {
      get(): string {
        return this.$store.getters.memberCode;
      },
      set(value: string) {
        this.$store.commit('setMemberCode', value);
      },
    },

    selectableMembers(): Client[] {
      // Filter out the owner member
      const filtered = this.$store.getters.selectableMembers.filter(
        (client: Client) => {
          return !(
            client.member_class === this.reservedMember.memberClass &&
            client.member_code === this.reservedMember.memberCode
          );
        },
      );
      return filtered;
    },

    duplicateClient(): boolean {
      if (!this.memberClass || !this.memberCode) {
        return false;
      }

      // Check that the info doesn't match the reserved member (owner member)
      return !(
        this.reservedMember.memberClass.toLowerCase() !==
          this.memberClass.toLowerCase() ||
        this.reservedMember.memberCode.toLowerCase() !==
          this.memberCode.toLowerCase()
      );
    },
  },

  watch: {
    async memberCode(val) {
      // Set wizard mode to default (full)
      this.$store.commit('setAddMemberWizardMode', AddMemberWizardModes.FULL);

      // Needs to be done here, because the watcher runs before the setter
      validate(this.memberCode, 'required|xrdIdentifier', {
        // name is not used, but if it's undefined there is a warning in browser console
        name: 'addClient.memberCode',
      }).then((result) => {
        if (result.valid) {
          this.isMemberCodeValid = true;
          if (isEmpty(val) || isEmpty(this.memberClass)) {
            return;
          }
          this.checkClient();
        } else {
          this.isMemberCodeValid = false;
        }
      });
    },
    memberClass(val): void {
      // Set wizard mode to default (full)
      this.$store.commit('setAddMemberWizardMode', AddMemberWizardModes.FULL);
      if (isEmpty(val) || isEmpty(this.memberCode)) {
        return;
      }
      this.checkClient();
    },

    memberClassesCurrentInstance(val): void {
      // Set first member class selected as default when the list is updated
      if (val?.length === 1) {
        this.memberClass = val[0];
      }
    },
  },

  mounted() {
    this.$refs.memberCodeVP;
  },
  created() {
    that = this;
    this.$store.commit('setAddMemberWizardMode', AddMemberWizardModes.FULL);
    this.$store.dispatch(
      'fetchSelectableMembers',
      that.currentSecurityServer.instance_id,
    );
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      this.$emit('done');
    },
    saveSelectedClient(selectedMember: Client): void {
      this.$store.dispatch('setSelectedMember', selectedMember);
      this.showSelectClient = false;
    },
    checkClient(): void {
      // check if the identifier is valid
      if (!this.isMemberCodeValid) {
        return;
      }
      this.checkRunning = true;

      // Find if the selectable clients array has a match
      const tempClient = this.selectableMembers.find((client: Client) => {
        return (
          client.member_code === this.memberCode &&
          client.member_class === this.memberClass
        );
      });

      // Fill the name "field" if it's available or set it undefined
      this.$store.commit('setSelectedMemberName', tempClient?.member_name);

      // Pass the arguments so that we use the validated information instead of the state at that time
      this.checkClientDebounce(this.memberClass, this.memberCode);
    },
    checkClientDebounce: debounce((memberClass: string, memberCode: string) => {
      // Debounce is used to reduce unnecessary api calls
      // Search tokens for suitable CSR:s and certificates
      that.$store
        .dispatch('searchTokens', {
          instanceId: that.reservedMember.instanceId,
          memberClass: memberClass,
          memberCode: memberCode,
        })
        .then(
          () => {
            that.checkRunning = false;
          },
          (error: Error) => {
            that.$store.dispatch('showError', error);
            that.checkRunning = true;
          },
        );
    }, 600),
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
