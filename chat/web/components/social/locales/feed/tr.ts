import type { FeedDict } from '../../feed-text'

const tr: FeedDict = {
  // the feed
  'feed.loadFailed': 'Gönderiler yüklenemedi',

  // a post
  'post.like': 'Beğen',
  'post.likes.one': '{n} beğeni',
  'post.likes.other': '{n} beğeni',
  'post.comment': 'Yorum yap',
  'post.comments.one': '{n} yorum',
  'post.comments.other': '{n} yorum',
  'post.menu': 'Diğer seçenekler',
  'post.report': 'Gönderiyi şikâyet et',
  'post.delete': 'Gönderiyi sil',
  'post.deleteTitle': 'Bu gönderi silinsin mi?',
  'post.deleteBody': 'Beğenileri ve yorumlarıyla birlikte herkes için kaldırılır.',
  'post.deleted': 'Gönderi silindi',
  'post.public': 'Herkes',
  'post.friendsOnly': 'Arkadaşlar',
  'post.by': '{name} gönderisi',
  'post.photo': 'Fotoğraf: {name}',
  'post.readMore': 'devamı',
  'post.gone': 'Bu gönderi kullanılamıyor',
  'post.goneBody': 'Silinmiş olabilir ya da sadece paylaşanın arkadaşları görebiliyor.',
  'post.reportTitle': 'Bu gönderiyi şikâyet et',
  'post.reported': 'Teşekkürler. Bu gönderiyi inceleyeceğiz.',
  'post.viewProfile': 'Profili gör',

  // comments
  'comments.title': 'Yorumlar',
  'comments.none': 'Henüz yorum yok',
  'comments.noneBody': 'Sohbeti sen başlat.',
  'comments.placeholder': 'Yorum ekle…',
  'comments.placeholderReply': '{name} kişisine yanıt ver…',
  'comments.label': 'Yorumun',
  'comments.send': 'Paylaş',
  'comments.reply': 'Yanıtla',
  'comments.replyingTo': '{name} kişisine yanıt veriliyor',
  'comments.cancelReply': 'Yanıtı iptal et',
  'comments.remove': 'Kaldır',
  'comments.removed': 'Yorum kaldırıldı',
  'comments.report': 'Şikâyet et',
  'comments.reportTitle': 'Bu yorumu şikâyet et',
  'comments.reported': 'Teşekkürler. Bu yorumu inceleyeceğiz.',
  'comments.more': 'Daha fazla yorum göster',
  'comments.loadFailed': 'Yorumlar yüklenemedi',

  // the composer
  'compose.audience': 'Bunu kimler görebilir',
  'compose.friends': 'Arkadaşlar',
  'compose.everyone': 'Herkes',
  'compose.friendsHint': 'Arkadaşların ve sohbet ettiğin kişiler.',
  'compose.everyoneHint': 'Profilini açan yaari’deki herkes. Seni ekleyebilirler de.',
  'compose.checking': 'Fotoğraf kontrol ediliyor…',
  'compose.nude': 'Bu fotoğraf çıplaklık içeriyor gibi görünüyor, bu yüzden paylaşılamaz',

  // profile style
  'style.status': 'Durum',
  'style.statusPlaceholder': 'Bugün modun ne?',
  'style.languages': 'Konuştuğum diller',
  'style.languagesHint': 'En fazla 6 tane seç.',
  'style.languagesMax': 'En fazla 6 dil seçebilirsin',
  'style.prompts': 'Sorular',
  'style.promptsHint': 'En fazla 3 tanesini yanıtla. İnsanlara sohbete başlamak için kolay bir yol sunar.',
  'style.promptAdd': 'Soru ekle',
  'style.promptRemove': 'Bu soruyu kaldır',
  'style.promptQuestion': 'Soru',
  'style.promptAnswer': 'Cevabın',
  'style.saved': 'Profil güncellendi',
  'style.problem.status': 'Durumun en fazla 60 karakter olabilir',
  'style.problem.answer': 'Cevaplar en fazla 120 karakter olabilir',
  'prompt.weekend': 'Mükemmel hafta sonum…',
  'prompt.food': 'Uğruna yola çıkacağım yemek…',
  'prompt.song': 'Sürekli dinlediğim şarkı…',
  'prompt.travel': 'Sıradaki seyahatim…',
  'prompt.talk': 'Anlaşırız, eğer…',
  'prompt.laugh': 'Beni güldüren şey…',
  'prompt.goal': 'Bu yıl istiyorum ki…',
  'prompt.secret': 'Çok az kişinin bildiği bir şey…',

  // a profile
  'profile.speaks': 'Konuştuğu diller',
  'style.mbtiHint': "İsteğe bağlı. Temizlemek için tekrar dokun.",
  'post.likers': "Beğeniler",
}

export default tr
