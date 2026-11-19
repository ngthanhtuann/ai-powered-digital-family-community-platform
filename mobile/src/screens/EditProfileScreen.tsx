// Chính chủ sửa hồ sơ gia phả của mình trên mobile, như form "Chỉnh sửa hồ sơ" của web (BR-GEN-20, BR-DIR-03; SCRUM-128).
import { zodResolver } from '@hookform/resolvers/zod';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useEffect, useState } from 'react';
import { Controller, useForm, type Path } from 'react-hook-form';
import { Text, View } from 'react-native';
import { z } from 'zod';
import { ErrorPanel, toUploadAsset } from '../components/community-ui';
import { applyServerError } from '../components/form-errors';
import { PersonAvatar } from '../components/genealogy-ui';
import {
  Button,
  Card,
  Loading,
  Notice,
  Screen,
  TextField,
  type NoticeMessage,
} from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { genealogyApi, toPersonInput } from '../services/genealogy';
import { heritageApi } from '../services/heritage';
import { colors, text } from '../theme';

const year = z.string().refine((v) => v === '' || /^\d{4}$/.test(v), 'Năm gồm 4 chữ số');
const schema = z.object({
  fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(150, 'Tối đa 150 ký tự'),
  birthDate: z
    .string()
    .trim()
    .refine((v) => /^\d{4}-\d{2}-\d{2}$/.test(v), 'Ngày sinh dạng YYYY-MM-DD'),
  birthplace: z.string().trim().max(255, 'Tối đa 255 ký tự'),
  currentProvince: z.string().trim().max(100, 'Tối đa 100 ký tự'),
  currentAddress: z.string().trim().max(255, 'Tối đa 255 ký tự'),
  note: z.string().trim().max(2000, 'Tối đa 2000 ký tự'),
  jobTitle: z.string().trim().max(150, 'Tối đa 150 ký tự'),
  company: z.string().trim().max(200, 'Tối đa 200 ký tự'),
  workLocation: z.string().trim().max(200, 'Tối đa 200 ký tự'),
  school: z.string().trim().max(200, 'Tối đa 200 ký tự'),
  degree: z.string().trim().max(100, 'Tối đa 100 ký tự'),
  graduationYear: year,
});
type Values = z.infer<typeof schema>;
const FIELDS: { name: Path<Values>; label: string; multiline?: boolean; numeric?: boolean }[] = [
  { name: 'fullName', label: 'Họ tên' },
  { name: 'birthDate', label: 'Ngày sinh (YYYY-MM-DD)' },
  { name: 'birthplace', label: 'Quê quán' },
  { name: 'currentProvince', label: 'Tỉnh/thành đang sống' },
  { name: 'currentAddress', label: 'Địa chỉ' },
  { name: 'note', label: 'Giới thiệu', multiline: true },
  { name: 'jobTitle', label: 'Nghề nghiệp, chức danh' },
  { name: 'company', label: 'Nơi làm việc' },
  { name: 'workLocation', label: 'Địa điểm làm việc' },
  { name: 'school', label: 'Trường' },
  { name: 'degree', label: 'Bằng cấp' },
  { name: 'graduationYear', label: 'Năm tốt nghiệp', numeric: true },
];
const CAREER_START = 6; // từ jobTitle là phần nghề nghiệp, học vấn

/** Chưa có hồ sơ nghề, học vấn (404) thì form để trống. */
const orNull = <T,>(promise: Promise<T>) =>
  promise.catch((e: unknown) => {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  });

export function EditProfileScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'EditProfile'>) {
  const { personId } = route.params;
  const qc = useQueryClient();
  const [notice, setNotice] = useState<NoticeMessage | null>(null);
  const person = useQuery({
    queryKey: ['person', personId],
    queryFn: () => genealogyApi.person(personId),
  });
  const profession = useQuery({
    queryKey: ['profession-profile', personId],
    queryFn: () => orNull(genealogyApi.professionProfile(personId)),
  });
  const education = useQuery({
    queryKey: ['education-profile', personId],
    queryFn: () => orNull(heritageApi.educationProfile(personId)),
  });
  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  useEffect(() => {
    const p = person.data;
    if (!p || profession.isPending || education.isPending) return;
    reset({
      fullName: p.fullName ?? '',
      birthDate: p.birthDate ?? '',
      birthplace: p.birthplace ?? '',
      currentProvince: p.currentProvince ?? '',
      currentAddress: p.currentAddress ?? '',
      note: p.note ?? '',
      jobTitle: profession.data?.jobTitle ?? '',
      company: profession.data?.company ?? '',
      workLocation: profession.data?.workLocation ?? '',
      school: education.data?.school ?? '',
      degree: education.data?.degree ?? '',
      graduationYear: education.data?.graduationYear ? String(education.data.graduationYear) : '',
    });
  }, [
    person.data,
    profession.data,
    education.data,
    profession.isPending,
    education.isPending,
    reset,
  ]);

  const refresh = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ['person'] }),
      qc.invalidateQueries({ queryKey: ['profession-profile', personId] }),
      qc.invalidateQueries({ queryKey: ['education-profile', personId] }),
      qc.invalidateQueries({ queryKey: ['tree'] }),
      qc.invalidateQueries({ queryKey: ['posts'] }),
    ]);

  const avatar = useMutation({
    mutationFn: async () => {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        quality: 0.8,
      });
      if (result.canceled || !person.data) return false;
      const media = await genealogyApi.uploadPersonPhoto(personId, toUploadAsset(result.assets[0]));
      await genealogyApi.updatePerson(personId, {
        ...toPersonInput(person.data),
        avatarMediaId: media.id,
      });
      return true;
    },
    onSuccess: async (changed) => {
      if (!changed) return;
      await refresh();
      setNotice({ variant: 'success', title: 'Đã đổi ảnh đại diện', message: '' });
    },
    onError: (e) =>
      setNotice({ variant: 'error', title: 'Không đổi được ảnh', message: (e as Error).message }),
  });

  const onSubmit = handleSubmit(async (v) => {
    if (!person.data) return;
    setNotice(null);
    try {
      await genealogyApi.updatePerson(personId, {
        ...toPersonInput(person.data),
        fullName: v.fullName,
        birthDate: v.birthDate,
        birthYear: null,
        birthplace: v.birthplace || null,
        currentProvince: v.currentProvince || null,
        currentAddress: v.currentAddress || null,
        note: v.note || null,
      });
      // Để trống nghề hoặc trường thì giữ hồ sơ cũ, như web.
      if (v.jobTitle) {
        await genealogyApi.saveProfession(personId, {
          jobTitle: v.jobTitle,
          company: v.company || null,
          workLocation: v.workLocation || null,
        });
      }
      if (v.school) {
        await genealogyApi.saveEducation(personId, {
          school: v.school,
          degree: v.degree || null,
          graduationYear: v.graduationYear ? Number(v.graduationYear) : null,
        });
      }
      await refresh();
      navigation.goBack();
    } catch (e) {
      setNotice(
        applyServerError(
          e,
          FIELDS.map((f) => f.name),
          setError,
        ),
      );
    }
  });

  if (person.isPending || profession.isPending || education.isPending) {
    return (
      <Screen>
        <Loading />
      </Screen>
    );
  }
  if (person.isError) {
    return (
      <Screen>
        <ErrorPanel error={person.error} retry={() => person.refetch()} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Card title="Ảnh đại diện">
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <PersonAvatar
            name={person.data.fullName}
            gender={person.data.gender}
            mediaId={person.data.avatarMediaId}
            size={64}
          />
          <View style={{ flex: 1 }}>
            <Button
              title="Đổi ảnh đại diện"
              variant="secondary"
              loading={avatar.isPending}
              onPress={() => avatar.mutate()}
            />
          </View>
        </View>
        <Text style={[text.caption, { color: colors.textSecondary }]}>
          Ảnh JPG, PNG hoặc WebP, tối đa 5 MB.
        </Text>
      </Card>
      <Card title="Thông tin cá nhân" description="Chỉ bạn sửa được thông tin của mình.">
        {FIELDS.map((f, i) => (
          <View key={f.name}>
            {i === CAREER_START ? (
              <Text style={[text.sectionTitle, { color: colors.textPrimary, marginVertical: 8 }]}>
                Nghề nghiệp và học vấn
              </Text>
            ) : null}
            <Controller
              control={control}
              name={f.name}
              render={({ field }) => (
                <TextField
                  label={f.label}
                  value={field.value}
                  onChangeText={field.onChange}
                  onBlur={field.onBlur}
                  multiline={f.multiline}
                  keyboardType={f.numeric ? 'number-pad' : 'default'}
                  error={errors[f.name]?.message}
                />
              )}
            />
          </View>
        ))}
        {notice ? <Notice {...notice} /> : null}
        <Button title="Lưu thay đổi" loading={isSubmitting} onPress={onSubmit} />
      </Card>
    </Screen>
  );
}
