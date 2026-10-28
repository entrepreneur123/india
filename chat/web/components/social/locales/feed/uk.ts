import type { FeedDict } from '../../feed-text'

const uk: FeedDict = {
  // the feed
  'feed.loadFailed': 'Не вдалося завантажити дописи',

  // a post
  'post.like': 'Подобається',
  'post.likes.one': '{n} вподобання',
  'post.likes.few': '{n} вподобання',
  'post.likes.many': '{n} вподобань',
  'post.likes.other': '{n} вподобання',
  'post.comment': 'Коментувати',
  'post.comments.one': '{n} коментар',
  'post.comments.few': '{n} коментарі',
  'post.comments.many': '{n} коментарів',
  'post.comments.other': '{n} коментаря',
  'post.menu': 'Більше',
  'post.report': 'Поскаржитися на допис',
  'post.delete': 'Видалити допис',
  'post.deleteTitle': 'Видалити цей допис?',
  'post.deleteBody': 'Його буде видалено для всіх разом із вподобаннями й коментарями.',
  'post.deleted': 'Допис видалено',
  'post.public': 'Усі',
  'post.friendsOnly': 'Друзі',
  'post.by': 'Допис: {name}',
  'post.photo': 'Фото: {name}',
  'post.readMore': 'ще',
  'post.gone': 'Цей допис недоступний',
  'post.goneBody': 'Його видалили, або його можуть бачити лише друзі автора.',
  'post.reportTitle': 'Поскаржитися на допис',
  'post.reported': 'Дякуємо. Ми перевіримо цей допис.',
  'post.viewProfile': 'Відкрити профіль',

  // comments
  'comments.title': 'Коментарі',
  'comments.none': 'Коментарів поки немає',
  'comments.noneBody': 'Почніть обговорення.',
  'comments.placeholder': 'Додайте коментар…',
  'comments.placeholderReply': 'Відповідь: {name}…',
  'comments.label': 'Ваш коментар',
  'comments.send': 'Опублікувати',
  'comments.reply': 'Відповісти',
  'comments.replyingTo': 'Відповідь: {name}',
  'comments.cancelReply': 'Скасувати відповідь',
  'comments.remove': 'Видалити',
  'comments.removed': 'Коментар видалено',
  'comments.report': 'Поскаржитися',
  'comments.reportTitle': 'Поскаржитися на коментар',
  'comments.reported': 'Дякуємо. Ми перевіримо цей коментар.',
  'comments.more': 'Показати ще коментарі',
  'comments.loadFailed': 'Не вдалося завантажити коментарі',

  // the composer
  'compose.audience': 'Хто це побачить',
  'compose.friends': 'Друзі',
  'compose.everyone': 'Усі',
  'compose.friendsHint': 'Ваші друзі й люди, з якими ви спілкувалися.',
  'compose.everyoneHint': 'Усі в yaari, хто відкриє ваш профіль. Вони також зможуть додати вас.',
  'compose.checking': 'Перевіряємо фото…',
  'compose.nude': 'Схоже, на фото оголеність, тому його не можна опублікувати',

  // profile style
  'style.status': 'Статус',
  'style.statusPlaceholder': 'Який у вас сьогодні настрій?',
  'style.languages': 'Якими мовами я говорю',
  'style.languagesHint': 'Виберіть до 6.',
  'style.languagesMax': 'Можна вибрати не більше 6 мов',
  'style.saved': 'Профіль оновлено',
  'style.problem.status': 'Статус — не довше 60 символів',
  'style.problem.answer': 'Відповіді — не довше 120 символів',

  // a profile
  'profile.speaks': 'Мови',
  'style.mbtiHint': "Необов’язково. Натисніть ще раз, щоб прибрати.",
  'post.likers': "Вподобання",
}

export default uk
